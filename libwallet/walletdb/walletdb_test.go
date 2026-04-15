package walletdb

import (
	"bufio"
	"bytes"
	"context"
	"crypto/rand"
	"database/sql"
	"fmt"
	"io"
	"math"
	"os"
	"os/exec"
	"path"
	"testing"
	"time"

	_ "github.com/jinzhu/gorm/dialects/sqlite"
)

// TestMain intercepts subprocess invocations from TestBusyTimeout.
func TestMain(m *testing.M) {
	// When LOCK_HOLDER_DB_PATH is set, this process acts as the lock holder:
	// it acquires an exclusive SQLite lock, signals READY to stdout, waits for
	// a byte on stdin, then releases the lock and exits.
	// When the env var is absent, it runs the test suite normally.
	dbPath := os.Getenv("LOCK_HOLDER_DB_PATH")
	if dbPath != "" {
		runLockHolder(dbPath)
		return
	}
	os.Exit(m.Run())
}

// runLockHolder acquires an exclusive SQLite lock on dbPath, signals "READY" to stdout,
// and holds the lock until it receives any byte on stdin.
func runLockHolder(dbPath string) {
	rawDB, err := sql.Open("sqlite3", dbPath)
	if err != nil {
		fmt.Fprintln(os.Stderr, "open error:", err)
		os.Exit(1)
	}
	defer rawDB.Close()

	ctx := context.Background()
	conn, err := rawDB.Conn(ctx)
	if err != nil {
		fmt.Fprintln(os.Stderr, "conn error:", err)
		os.Exit(1)
	}
	defer conn.Close()

	if _, err := conn.ExecContext(ctx, "BEGIN EXCLUSIVE"); err != nil {
		fmt.Fprintln(os.Stderr, "BEGIN EXCLUSIVE error:", err)
		os.Exit(1)
	}

	fmt.Println("READY")

	// Block until the parent writes anything to stdin (signals us to release).
	buf := make([]byte, 1)
	os.Stdin.Read(buf)

	conn.ExecContext(ctx, "ROLLBACK")
}

func TestOpen(t *testing.T) {
	dir, err := os.MkdirTemp("", "libwallet")
	if err != nil {
		panic(err)
	}

	db, err := Open(path.Join(dir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
}

func TestInvoices(t *testing.T) {
	dir, err := os.MkdirTemp("", "libwallet")
	if err != nil {
		panic(err)
	}

	db, err := Open(path.Join(dir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	shortChanId := uint64((math.MaxInt64 - 5) | (1 << 63))
	paymentHash := randomBytes(32)

	err = db.CreateInvoice(&Invoice{
		Preimage:      randomBytes(32),
		PaymentHash:   paymentHash,
		PaymentSecret: randomBytes(32),
		KeyPath:       "34/56",
		ShortChanId:   shortChanId,
		State:         InvoiceStateRegistered,
	})
	if err != nil {
		t.Fatal(err)
	}

	count, err := db.CountUnusedInvoices()
	if err != nil {
		t.Fatal(err)
	}
	if count != 1 {
		t.Fatalf("expected to find 1 unused invoice, got %d", count)
	}

	inv, err := db.FindByPaymentHash(paymentHash)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(inv.PaymentHash, paymentHash) {
		t.Fatal("expected invoice payment hash does not match")
	}

	inv, err = db.FindFirstUnusedInvoice()
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(inv.PaymentHash, paymentHash) {
		t.Fatal("expected invoice payment hash does not match")
	}
	if inv.ShortChanId != shortChanId {
		t.Fatal("expected invoice short channel id does not match")
	}

	err = db.SaveInvoice(inv)
	if err != nil {
		t.Fatal(err)
	}
	if inv.ShortChanId != shortChanId {
		t.Fatal("expected invoice short channel id does not match")
	}
}

// TestBusyTimeout verifies that _busy_timeout prevents "database is locked" errors
// when a write is attempted while another connection holds an exclusive lock.
func TestBusyTimeout(t *testing.T) {
	dir, err := os.MkdirTemp("", "libwallet")
	if err != nil {
		t.Fatal(err)
	}
	dbPath := path.Join(dir, "test.db")

	// Open and close connection to ensure migrations are executed before acquiring any lock.
	db, err := Open(dbPath)
	if err != nil {
		t.Fatal(err)
	}
	db.Close()

	db, err = Open(dbPath)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	lh := startLockHolder(t, dbPath)
	defer lh.cleanup()

	// Release the lock after 500ms while the writer is retrying.
	// With _busy_timeout=1000 (our current setting), SQLite retries for up to 1s before giving up,
	// so the write should succeed once the lock is released.
	go func() {
		time.Sleep(500 * time.Millisecond)
		lh.release()
	}()

	value := "test"
	err = db.NewKeyValueRepository().Save("someKey", &value)
	if err != nil {
		t.Errorf("expected success, got: %v", err)
	}
}

type lockHolder struct {
	cmd   *exec.Cmd
	stdin io.WriteCloser
}

// startLockHolder re-executes the test binary as a subprocess that acquires an exclusive SQLite lock on dbPath
// and holds it until released.
// It returns once the subprocess has signalled that the lock is held.
//
// A separate process is required because SQLite uses POSIX fcntl() advisory locks,
// which are per open file description.
// On Linux, closing any file descriptor to a file releases all fcntl locks held by that process,
// regardless of how many other descriptors remain open.
// This means that a lock acquired from within the same process
// would not reliably reproduce the SQLITE_BUSY contention
func startLockHolder(t *testing.T, dbPath string) *lockHolder {
	t.Helper()

	exe, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}

	cmd := exec.Command(exe)
	cmd.Env = append(os.Environ(), "LOCK_HOLDER_DB_PATH="+dbPath)

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		t.Fatal(err)
	}
	stdin, err := cmd.StdinPipe()
	if err != nil {
		t.Fatal(err)
	}

	err = cmd.Start()
	if err != nil {
		t.Fatal(err)
	}

	// Wait for the subprocess to signal it holds the lock.
	scanner := bufio.NewScanner(stdout)
	for scanner.Scan() {
		if scanner.Text() == "READY" {
			return &lockHolder{cmd: cmd, stdin: stdin}
		}
	}

	cmd.Process.Kill()
	t.Fatal("lock holder subprocess did not signal READY")
	return nil
}

func (lh *lockHolder) release() {
	lh.stdin.Write([]byte("x"))
}

func (lh *lockHolder) cleanup() {
	lh.stdin.Close()
	lh.cmd.Wait()
}

func randomBytes(count int) []byte {
	buf := make([]byte, count)
	_, err := rand.Read(buf)
	if err != nil {
		panic(err)
	}
	return buf
}
