package scanner

import (
	"fmt"
	"log/slog"
	"sync"
	"time"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/electrum"
)

const taskTimeout = 15 * time.Minute
const batchSize = 100

// Scanner finds unspent outputs and their transactions when given a map of addresses.
//
// It implements multi-server support, batching feature detection and use, concurrency control,
// timeouts and cancellations, and provides a channel-based interface.
//
// Servers are provided by a ServerProvider instance, and rotated when unreachable or faulty. We
// trust ServerProvider to prioritize good targets.
//
// Batching is leveraged when supported by a particular server, falling back to sequential requests
// for single addresses (which is much slower, but can get us out of trouble when better servers are
// not available).
//
// Timeouts and cancellations are an internal affair, not configurable by callers. See taskTimeout
// declared above.
//
// Concurrency control works by using an electrum.Pool, limiting access to clients, and not an
// internal worker pool. This is the Go way (limiting access to resources rather than having a fixed
// number of parallel goroutines), and (more to the point) semantically correct. We don't care
// about the number of concurrent workers, what we want to avoid is too many connections to
// Electrum servers.
type Scanner struct {
	pool        *electrum.Pool
	servers     *electrum.ServerProvider
	log         *slog.Logger
	chainParams *chaincfg.Params
}

// Report contains information about an ongoing scan.
type Report struct {
	ScannedAddresses int
	UtxosFound       []*Utxo
	Err              error
}

// Utxo references a transaction output, plus the associated MuunAddress and script.
type Utxo struct {
	TxID        string
	OutputIndex int
	Amount      int64
	Address     libwallet.MuunAddress
	Script      []byte
}

// scanContext contains the synchronization objects for a single Scanner round, to manage Tasks.
type scanContext struct {
	// Task management:
	addresses   chan libwallet.MuunAddress
	results     chan *scanTaskResult
	stopScan    chan struct{}
	stopCollect chan struct{}
	wg          *sync.WaitGroup

	// Progress reporting:
	reports     chan *Report
	reportCache *Report
}

// NewScanner creates an initialized Scanner.
func NewScanner(connectionPool *electrum.Pool, electrumProvider *electrum.ServerProvider, chainParams *chaincfg.Params) *Scanner {
	return &Scanner{
		pool:        connectionPool,
		servers:     electrumProvider,
		log:         slog.Default().With("prefix", "Scanner"),
		chainParams: chainParams,
	}
}

// Scan an address space and return all relevant transactions for a sweep.
func (s *Scanner) Scan(addresses chan libwallet.MuunAddress) <-chan *Report {
	var waitGroup sync.WaitGroup

	// Create the Context that goroutines will share:
	ctx := &scanContext{
		addresses:   addresses,
		results:     make(chan *scanTaskResult),
		stopScan:    make(chan struct{}),
		stopCollect: make(chan struct{}),
		wg:          &waitGroup,

		reports: make(chan *Report),
		reportCache: &Report{
			ScannedAddresses: 0,
			UtxosFound:       []*Utxo{},
		},
	}

	// Start the scan in background:
	go s.startCollect(ctx)
	go s.startScan(ctx)

	return ctx.reports
}

func (s *Scanner) startCollect(ctx *scanContext) {
	// Collect all results until the done signal, or abort on the first error:
	for {
		select {
		case result := <-ctx.results:
			s.log.Info(fmt.Sprintf("Scanned %d, found %d", len(result.Task.addresses), len(result.Utxos)), "error", result.Err)

			newReport := *ctx.reportCache // create a new private copy
			ctx.reportCache = &newReport

			if result.Err != nil {
				s.log.Error("Scan failed", "error", result.Err)
				ctx.reportCache.Err = result.Err
				ctx.reports <- ctx.reportCache

				close(ctx.stopScan) // failed after several retries, we give up and terminate all tasks
				close(ctx.reports)  // close the report channel to let callers know we're done
				return
			}

			ctx.reportCache.ScannedAddresses += len(result.Task.addresses)
			ctx.reportCache.UtxosFound = append(ctx.reportCache.UtxosFound, result.Utxos...)
			ctx.reports <- ctx.reportCache

		case <-ctx.stopCollect:
			close(ctx.reports) // close the report channel to let callers know we're done
			return
		}
	}
}

func (s *Scanner) startScan(ctx *scanContext) {
	s.log.Info("Scan started")

	batches := streamBatches(ctx.addresses)

	var client *electrum.Client

	for batch := range batches {
		// Stop the loop until a client becomes available, or the scan is canceled:
		select {
		case <-ctx.stopScan:
			return

		case client = <-s.pool.Acquire():
		}

		// Start scanning this address in background:
		ctx.wg.Add(1)

		go func(batch []libwallet.MuunAddress) {
			defer s.pool.Release(client)
			defer ctx.wg.Done()

			s.scanBatch(ctx, client, batch)
		}(batch)
	}

	// Wait for all tasks that are still executing to complete:
	ctx.wg.Wait()
	s.log.Info("Scan complete")

	// Signal to the collector that this Context has no more pending work:
	close(ctx.stopCollect)
}

func (s *Scanner) scanBatch(ctx *scanContext, client *electrum.Client, batch []libwallet.MuunAddress) {
	// NOTE:
	// We begin by building the task, passing our selected Client. Since we're choosing the instance,
	// it's our job to control acquisition and release of Clients to prevent sharing (remember,
	// clients are single-user). The task won't enforce this safety measure (it can't), it's fully
	// up to us.
	task := &scanTask{
		servers:     s.servers,
		client:      client,
		addresses:   batch,
		timeout:     taskTimeout,
		exit:        ctx.stopCollect,
		chainParams: s.chainParams,
	}

	// Do the thing and send back the result:
	ctx.results <- task.Execute()
}

func streamBatches(addresses chan libwallet.MuunAddress) chan []libwallet.MuunAddress {
	batches := make(chan []libwallet.MuunAddress)

	go func() {
		var nextBatch []libwallet.MuunAddress

		for address := range addresses {
			// Add items to the batch until we reach the limit:
			nextBatch = append(nextBatch, address)

			if len(nextBatch) < batchSize {
				continue
			}

			// Send back the batch and start over:
			batches <- nextBatch
			nextBatch = []libwallet.MuunAddress{}
		}

		// Send back an incomplete batch with any remaining addresses:
		if len(nextBatch) > 0 {
			batches <- nextBatch
		}

		close(batches)
	}()

	return batches
}
