package electrum

import "sync/atomic"

// ServerProvider manages a rotating server list, from which callers can pull server addresses.
type ServerProvider struct {
	nextIndex int32
	servers   []string
}

// NewServerProvider returns an initialized ServerProvider.
func NewServerProvider(servers []string) *ServerProvider {
	return &ServerProvider{
		nextIndex: -1,
		servers:   servers,
	}
}

// NextServer returns an address from the rotating list. It's thread-safe.
func (p *ServerProvider) NextServer() string {
	index := int(atomic.AddInt32(&p.nextIndex, 1))
	return p.servers[index%len(p.servers)]
}

// PublicServers list.
//
// This list was taken from Electrum repositories, keeping TLS servers and excluding onion URIs.
// It was then sorted into sections using the `cmd/survey` program, to prioritize the more reliable
// servers with batch support.
//
// See https://github.com/spesmilo/electrum/blob/master/electrum/servers.json
// See https://github.com/kyuupichan/electrumx/blob/master/electrumx/lib/coins.py
// See `cmd/survey/main.go`
var PublicServers = []string{
	// Fast servers with batching
	"electrum.coinext.com.br:50002",         // impl: ElectrumX 1.14.0, batching: true, ttc: 0.15, speed: 113, from:
	"fulcrum.sethforprivacy.com:50002",      // impl: Fulcrum 1.9.0, batching: true, ttc: 0.55, speed: 96, from:
	"mainnet.foundationdevices.com:50002",   // impl: Fulcrum 1.8.2, batching: true, ttc: 0.54, speed: 88, from:
	"btc.lastingcoin.net:50002",             // impl: Fulcrum 1.7.0, batching: true, ttc: 0.74, speed: 73, from:
	"vmd71287.contaboserver.net:50002",      // impl: Fulcrum 1.9.0, batching: true, ttc: 0.60, speed: 70, from:
	"de.poiuty.com:50002",                   // impl: Fulcrum 1.8.2, batching: true, ttc: 0.87, speed: 70, from:
	"electrum.jochen-hoenicke.de:50006",     // impl: Fulcrum 1.8.2, batching: true, ttc: 0.83, speed: 69, from:
	"btc.cr.ypto.tech:50002",                // impl: Fulcrum 1.9.0, batching: true, ttc: 0.81, speed: 65, from:
	"e.keff.org:50002",                      // impl: Fulcrum 1.8.2, batching: true, ttc: 0.82, speed: 65, from:
	"vmd104014.contaboserver.net:50002",     // impl: Fulcrum 1.9.0, batching: true, ttc: 0.58, speed: 64, from:
	"e2.keff.org:50002",                     // impl: Fulcrum 1.8.2, batching: true, ttc: 0.83, speed: 64, from:
	"fulcrum.grey.pw:51002",                 // impl: Fulcrum 1.9.0, batching: true, ttc: 0.81, speed: 63, from:
	"fortress.qtornado.com:443",             // impl: ElectrumX 1.16.0, batching: true, ttc: 0.84, speed: 62, from:
	"f.keff.org:50002",                      // impl: Fulcrum 1.8.2, batching: true, ttc: 0.89, speed: 62, from:
	"2ex.digitaleveryware.com:50002",        // impl: ElectrumX 1.16.0, batching: true, ttc: 0.71, speed: 61, from:
	"electrum.petrkr.net:50002",             // impl: Fulcrum 1.9.0, batching: true, ttc: 0.84, speed: 58, from:
	"electrum.stippy.com:50002",             // impl: ElectrumX 1.16.0, batching: true, ttc: 0.80, speed: 57, from:
	"electrum0.snel.it:50002",               // impl: ElectrumX 1.16.0, batching: true, ttc: 0.80, speed: 56, from:
	"ru.poiuty.com:50002",                   // impl: Fulcrum 1.8.2, batching: true, ttc: 0.99, speed: 56, from:
	"electrum.privateservers.network:50002", // impl: ElectrumX 1.15.0, batching: true, ttc: 0.85, speed: 49, from:
	"btc.electroncash.dk:60002",             // impl: Fulcrum 1.9.0, batching: true, ttc: 0.92, speed: 48, from:
	"bitcoin.aranguren.org:50002",           // impl: Fulcrum 1.8.2, batching: true, ttc: 1.19, speed: 48, from:
	"electrum.bitcoinserver.nl:50514",       // impl: Fulcrum 1.8.1, batching: true, ttc: 0.85, speed: 44, from:
	"btc.prompt.cash:61002",                 // impl: Fulcrum 1.8.1, batching: true, ttc: 1.22, speed: 44, from:
	"fulc.bot.nu:50002",                     // impl: Fulcrum 1.7.0, batching: true, ttc: 1.04, speed: 35, from:
	"bolt.schulzemic.net:50002",             // impl: Fulcrum 1.8.2, batching: true, ttc: 0.96, speed: 33, from:
	"node1.btccuracao.com:50002",            // impl: ElectrumX 1.16.0, batching: true, ttc: 0.90, speed: 25, from:

	// Other servers
	"xtrum.com:50002",                       // impl: ElectrumX 1.16.0, batching: true, ttc: 0.91, speed: 19, from: fulcrum.sethforprivacy.com:50002
	"electrum.bitaroo.net:50002",            // impl: ElectrumX 1.16.0, batching: true, ttc: 1.04, speed: 19, from:
	"btce.iiiiiii.biz:50002",                // impl: ElectrumX 1.16.0, batching: true, ttc: 1.07, speed: 19, from: electrum.coinext.com.br:50002
	"electrum.emzy.de:50002",                // impl: ElectrumX 1.16.0, batching: true, ttc: 1.17, speed: 19, from:
	"alviss.coinjoined.com:50002",           // impl: ElectrumX 1.15.0, batching: true, ttc: 1.15, speed: 17, from:
	"2AZZARITA.hopto.org:50002",             // impl: ElectrumX 1.16.0, batching: true, ttc: 0.81, speed: 16, from: electrum.coinext.com.br:50002
	"vmd104012.contaboserver.net:50002",     // impl: Fulcrum 1.9.0, batching: true, ttc: 1.25, speed: 16, from:
	"electrum.bitcoinlizard.net:50002",      // impl: ElectrumX 1.16.0, batching: true, ttc: 4.99, speed: 14, from:
	"btc.ocf.sh:50002",                      // impl: ElectrumX 1.16.0, batching: true, ttc: 1.19, speed: 12, from:
	"bitcoins.sk:56002",                     // impl: ElectrumX 1.14.0, batching: true, ttc: 0.80, speed: 11, from:
	"electrum-btc.leblancnet.us:50002",      // impl: ElectrumX 1.16.0, batching: true, ttc: 1.04, speed: 11, from:
	"helicarrier.bauerj.eu:50002",           // impl: ElectrumX 1.10.0, batching: true, ttc: 0.96, speed: 10, from:
	"electrum.neocrypto.io:50002",           // impl: ElectrumX 1.16.0, batching: true, ttc: 0.69, speed: 7, from:
	"caleb.vegas:50002",                     // impl: ElectrumX 1.16.0, batching: true, ttc: 0.71, speed: 7, from:
	"smmalis37.ddns.net:50002",              // impl: ElectrumX 1.16.0, batching: true, ttc: 0.77, speed: 7, from:
	"2azzarita.hopto.org:50002",             // impl: ElectrumX 1.16.0, batching: true, ttc: 0.78, speed: 7, from: fulcrum.sethforprivacy.com:50002
	"electrum.kendigisland.xyz:50002",       // impl: ElectrumX 1.16.0, batching: true, ttc: 0.79, speed: 7, from:
	"electrum.hsmiths.com:50002",            // impl: ElectrumX 1.10.0, batching: true, ttc: 0.90, speed: 7, from:
	"vmd63185.contaboserver.net:50002",      // impl: ElectrumX 1.16.0, batching: true, ttc: 0.92, speed: 7, from:
	"blkhub.net:50002",                      // impl: ElectrumX 1.16.0, batching: true, ttc: 0.94, speed: 7, from:
	"electrum.mmitech.info:50002",           // impl: ElectrumX 1.16.0, batching: true, ttc: 0.95, speed: 7, from:
	"elx.bitske.com:50002",                  // impl: ElectrumX 1.16.0, batching: true, ttc: 1.17, speed: 7, from:
	"bitcoin.lu.ke:50002",                   // impl: ElectrumX 1.16.0, batching: true, ttc: 1.21, speed: 7, from:
	"ex05.axalgo.com:50002",                 // impl: ElectrumX 1.16.0, batching: true, ttc: 1.21, speed: 7, from:
	"walle.dedyn.io:50002",                  // impl: ElectrumX 1.16.0, batching: true, ttc: 1.23, speed: 7, from:
	"eai.coincited.net:50002",               // impl: ElectrumX 1.16.0, batching: true, ttc: 0.34, speed: 6, from:
	"2electrumx.hopto.me:56022",             // impl: ElectrumX 1.16.0, batching: true, ttc: 0.93, speed: 6, from:
	"hodlers.beer:50002",                    // impl: ElectrumX 1.16.0, batching: true, ttc: 1.03, speed: 6, from:
	"kareoke.qoppa.org:50002",               // impl: ElectrumX 1.16.0, batching: true, ttc: 1.13, speed: 6, from:
	"ASSUREDLY.not.fyi:50002",               // impl: ElectrumX 1.16.0, batching: true, ttc: 0.85, speed: 4, from:
	"electrumx.alexridevski.net:50002",      // impl: ElectrumX 1.16.0, batching: true, ttc: 0.87, speed: 4, from:
	"assuredly.not.fyi:50002",               // impl: ElectrumX 1.16.0, batching: true, ttc: 0.90, speed: 4, from:
	"ragtor.duckdns.org:50002",              // impl: ElectrumX 1.16.0, batching: true, ttc: 1.07, speed: 4, from:
	"surely.not.fyi:50002",                  // impl: ElectrumX 1.16.0, batching: true, ttc: 1.20, speed: 4, from:
	"btc.electrum.bitbitnet.net:50002",      // impl: ElectrumX 1.15.0, batching: true, ttc: 0.71, speed: 3, from:
	"gods-of-rock.screaminglemur.net:50002", // impl: ElectrumX 1.15.0, batching: true, ttc: 0.92, speed: 3, from:
	"SURELY.not.fyi:50002",                  // impl: ElectrumX 1.16.0, batching: true, ttc: 1.35, speed: 3, from:
	"horsey.cryptocowboys.net:50002",        // impl: ElectrumX 1.15.0, batching: true, ttc: 0.61, speed: 2, from:
	"electrumx-btc.cryptonermal.net:50002",  // impl: ElectrumX 1.15.0, batching: true, ttc: 0.83, speed: 1, from:
	"electrum.coineuskal.com:50002",         // impl: ElectrumX 1.15.0, batching: true, ttc: 1.73, speed: 0, from: electrum.coinext.com.br:50002
}
