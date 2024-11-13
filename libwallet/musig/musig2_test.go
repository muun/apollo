package musig

import (
	"bytes"
	"encoding/hex"
	"fmt"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
)

// SerializePublicKey either serializes with 32 byte x-only or 33 bytes compressed
// serializations depending on the MuSig version
func SerializePublicKey(bipVersion MusigVersion, key *secp256k1.PublicKey) []byte {
	pub := key.SerializeCompressed()
	xOnly := bipVersion == Musig2v040Muun
	if xOnly {
		return pub[1:]
	}
	return pub
}

func hexDecode(keyStr string) []byte {
	keyBytes, _ := hex.DecodeString(keyStr)
	return keyBytes
}

func TestMuSig2Tests2of2(t *testing.T) {
	t.Parallel()

	testCases := []struct {
		name        string
		version     MusigVersion
		expectedErr string

		userKey              string
		muunKey              string
		tweak                *MuSig2Tweaks
		msg                  string
		muunSessionId        string
		userSessionId        string
		combinedPubUntweaked string
		combinedPub          string
		userNonce            string
		muunNonce            string
		muunPartialSignature string
		fullSignature        string
	}{{
		name:                 "sanity 1 (v040)",
		version:              Musig2v040Muun,
		userKey:              "507d881f0b5e1b12423cb0c84a196fb24227f3fe1540a1c7b20bf78d83de4533",
		muunKey:              "b6f14c73ee5269f5a13a11f48ad54306293ee134e924f680fcd35f615881105b",
		tweak:                KeySpendOnlyTweak(),
		msg:                  "ef2ecc1f48c0b28ccaf8f3a8c6477740d869964ebc152a2c5f93f19e7b84b103",
		userSessionId:        "5c9360026e39ad06251a27916dcf086a7b2deb6789c5dcd75ba10e540cf37e13",
		muunSessionId:        "cad3ec6737e2fb125d976bfe382441c59c6a4d46382bfab75e9d3f1b43a9b0a7",
		combinedPubUntweaked: "5ecd943b359fa0c52ba88c0395ca7f7bfa256c8d3c63609527856768185cab16",
		combinedPub:          "a2d0d99f9f2706846b18f070aaf95afb579332da15a58162987e48f59a90bb59",
		userNonce:            "03ce5914d3fd813391318ce9706227f325cb272a352b900be8bc9e813911f188b802ef1739fc0ca286ac81773daca310f6ff750f8ac0bbf11a253dcdcac93d2ede79",
		muunNonce:            "0294690a525328949fed6272f8308e48f7639ee270d83cad1d08b66e870d91a46e02e1a957ad994a6cbe6ee3db26a91e9031a38b92ec10b272ffd6cf8a303784bdb3",
		muunPartialSignature: "ef83958ce09b6859756a114a6e83d5654fb559064b6f21d78ddfd89e35f2a41c",
		fullSignature:        "6eda42c24fd743f88749c0ee662491fb5462db1f1e3bf3572eb75f4ae6ef6b19991c9d55038c56cb64132913b0ff8dc57bced644ed2ebbc8b3a3616ba373082d",
	}, {
		name:                 "sanity 1 (v100)",
		version:              Musig2v100,
		userKey:              "507d881f0b5e1b12423cb0c84a196fb24227f3fe1540a1c7b20bf78d83de4533",
		muunKey:              "b6f14c73ee5269f5a13a11f48ad54306293ee134e924f680fcd35f615881105b",
		tweak:                KeySpendOnlyTweak(),
		msg:                  "ef2ecc1f48c0b28ccaf8f3a8c6477740d869964ebc152a2c5f93f19e7b84b103",
		userSessionId:        "5c9360026e39ad06251a27916dcf086a7b2deb6789c5dcd75ba10e540cf37e13",
		muunSessionId:        "cad3ec6737e2fb125d976bfe382441c59c6a4d46382bfab75e9d3f1b43a9b0a7",
		combinedPubUntweaked: "03515ffb8569741ba605a113f9eb99bc81f9daacc8923d2578a31177e78aa0463c", // differs from 040
		combinedPub:          "03c367d7ef80b10687820dda279e0e6054dadccc30550c8eae7e21a945069cb7e0", // differs from 040
		userNonce:            "02b2c9e32786ab8612b4805e99f56086588d199486b7da5af99655402ffcc68cbd02aa0b18c2026742ed309fe00656cd9e68402bb10dea6aac05e69400f6f6c70ee5",
		muunNonce:            "0371c084c83326c3c721aeeefdf647d19d1417eba0ac9a09023bb363300eaeba7702539c3793f6a1a130b86a3168dbdb9ed686b04fdde57250dea20424047bc037ec",
		muunPartialSignature: "18422b132ac447af9e98db197d45becb26c83aa4fa658312dd8357e1e8309ce4",
		fullSignature:        "5e9034fe55b901308dd4751855e50a2181ec264b76fb5a986c87e78084202a9a83588f0e45e103645a01f313d7f3b03a5fcc5bc68ff7c41ae4d451271441d20b",
	}, {
		name:                 "sanity 2 (v040)",
		version:              Musig2v040Muun,
		userKey:              "5475f961bc879e5c34aaeb2b2de3272db2c2b849265b70836175dda4d9f4e323",
		muunKey:              "36bae62a1f853f8e0fd06a8dc92e40b279c8661cf331c098ee384430d51e0908",
		tweak:                KeySpendOnlyTweak(),
		msg:                  "fc5b875bf3cba4b01ff541b30029c3a2b40d8839c4e5a59d17b83d5ea0746a00",
		userSessionId:        "7097c074c9e821e9f6f8b2305bd79cd4e244c1968e139704261954cb6195fdb8",
		muunSessionId:        "577515cff85d3574a8f5f407b8017b3e1f7afeb604244ca5ddd946e6c4a9360a",
		combinedPub:          "d5ed5fbfd6598566ff101c671e7e2194604bd7b4aaa2e6913d691794ceaee3fd",
		userNonce:            "03162fcde2205a9e7c44ad646c904afb879ae8ae1e93086e245e9b152734c762a802bd70bfd7c80c7d5e7e91017920603916bb8475ee81d0f09d2049d514b12ae82e",
		muunNonce:            "028d5ce815841f0001b69f7cc82bb70ae1295a5bb51dfda73d0991b91216a6becd03238845a40801636793ddb0dd1ff5104a6e9afd4db4a439b5cc98e227785e079e",
		muunPartialSignature: "6b2e8c7878882e22a7dbdd1e89607105b2e64e95f72943a38908f9643900e11b",
		fullSignature:        "c1f24ddb83f8e6caf6fffc454a52f1e9dd67d9cfbccce309f0253361b94b7edcd6df44317e272f47a8a6dd823a65b7380055088bab914432f99ad92c277732aa",
	}, {
		name:                 "sanity 3 (v100)",
		version:              Musig2v100,
		userKey:              "e3be9721e2f3422d15a959b371b340c1f197fd9eb8a38b3f1801a759261f6766",
		muunKey:              "399508d30aa80e152bcfb6524ff900793d6a7ba0db0c1f6f74a80aca7482f131",
		tweak:                TapScriptTweak(hexDecode("83997adeb6df251a624c330f9f9b5dc1d825f1a5d73872d4aded55990f89a390")),
		msg:                  "d1752376fb27655a076f4dc2d3d1154f48faf98a2b3cde703b2c7c3bd6ca1054",
		userSessionId:        "d3fb624d2253acde57525b5b1b9ea4fba686de69acbb4ecabc87800a8fdd94b3",
		muunSessionId:        "dbab8752a87f26c85d9dad982a8d4b0e399079c6a9bf400cd2619d3422eee446",
		combinedPub:          "02128ded81436e25b77192b02771729eb9417b1c0bac848a657316cb69ead4d2fc",
		userNonce:            "022716e86001574bd783eafba6e4faf580760b5a5289f4481d198a8abf39af9c5903b5c0740167645a66f4ba37a8f090d96df4690de004d39525ccb320f59a5df7c6",
		muunNonce:            "0217f8ea11abe0b92070a43f7e2553f37f4afc7905165e190b266ed6f1aca2e76f02976491a364e4459fee8fae9bc82d0f3b08385607adfda238833d45ddc33be66e",
		muunPartialSignature: "a4843772adac7976e3133b68e4bf03dd93e9d233db9193a2e65ca2b352ae82d1",
		fullSignature:        "5dfafb89a5b7665a16f5391ada533e0c5f1283e126459e18c502383b21e60ede2b0c51206357a4974cd468cd8d6e9091b7fdcf82c33269e11bdba3e08286891a",
	}, {
		name:                 "sanity 4 (v040)",
		version:              Musig2v040Muun,
		userKey:              "cace2b7518d8866c8a41bb45aea06882dfb16e79d46fd02eb8af3a795fec5ada",
		muunKey:              "234221af43d249604ed3972c958677fb25e453e57cd808e2b32901dc6936fe94",
		tweak:                KeySpendOnlyTweak(),
		msg:                  "21a87f41d45b0a74d2d49328b580204f8b027ee4bb73bddfdf5388db8ab6bb49",
		userSessionId:        "b7aed480f9248d27d64744e5680ad91c6111d420a1e7c47db104b30fbc513899",
		muunSessionId:        "f7f7a39a0b434e4b9151f4f9797e12c4c1a8dc323c9efc4edd48ba6674c33762",
		combinedPubUntweaked: "ff591962a3be86cd58360ddd19212ce355a6fdd4566c219fe6bf0f6883a67f97",
		combinedPub:          "6fbfbe50f81503feb8163d01229e6e69d5a959186a0aa2d6e6881042fa0e0022",
		userNonce:            "0341f6cafeda073092125d7752f1d06f5d50dc8caf43eee212e2caf0ace8c03d4202c8e244bbc650734b8f186512b69798bb9d54a190a5c77a0d353068fe4806049d",
		muunNonce:            "03a3095a17a728b36f78d5428e397fcd4d7aaa1fd3d4aaaa2fb95a98a4092385b30361d094f79948ecd771bbf3f99a5fcd43bd023a3ea1bed8492213f4cf2327f4f7",
		muunPartialSignature: "a15f67ea4bfcb77b19cab3e44e37a9e03eb9921b80cdc8b3bf7d8a346af9139f",
		fullSignature:        "794e2dba30eacf59048559d471c382929b99ff86f657a760086492cdcb0c3a39e1f3d59367c585e083d3eadcdf8253b51c446f9d350c522ce7b2a5174b8b3513",
	}, {
		name:                 "sanity 5 (v100)",
		version:              Musig2v100,
		userKey:              "856067e727dda8e93fe2d92b481c2eb0726c5d70074c134567fd9870138fa9a9",
		muunKey:              "d65faacf0e9c75741cd487e9c0d17fa5fb23abb117b0b2d3817dbc9cc9a89eac",
		tweak:                TapScriptTweak(hexDecode("ce535b4f4059c9414d44afa5582b5650e6693739be869fda6c186cb2add3df04")),
		msg:                  "8ca8fbf5661b8cb7cf98eea348b8686c721b0d9d11f9ae7ea850322657c76dca",
		userSessionId:        "d74678ceb70ee4e875b52ab9a9b749d9882cfb3f184fbc13d9d96d1306ff9f54",
		muunSessionId:        "e92cd9db84ddea807401442036cc64878f03cdcc5030c78cecf187d4b166684f",
		combinedPub:          "02d26f1761a00851216ea9a87e479ab57bb03d38f7fd4d6d5a67f7954b7aed5517",
		userNonce:            "03d243bc9b76eb7212d9f71cfc8461fb3a32a0e72ffba9e41b056800722df329440363ab1202649637c2e6197f78edeb79bfc272a4f4ebac6d577baa6ec17eb8b7d2",
		muunNonce:            "03176ead83f618b25510ef0674d6a820fc23a6afe9ab6a507a68d251e0a615185203527ed0fa9ce1fd1c9993f41f52bd3edb441e069018a2637974558cde7a190cc2",
		muunPartialSignature: "7d8359f5159b7e3084000329abe5aa743d9535fa8c4ec37097b28d695371d6b2",
		fullSignature:        "4329e7aeb9467ace1af726b9f9101c4e4d7f776e348864cc9803a6928e83710de659b3bd81496e8497bed260d66070b982badd7eca20b3b32b2cb92f8c6fd902",
	}, {
		name:                 "sanity 6 (v040)",
		version:              Musig2v040Muun,
		userKey:              "a31e8d00ea74d66013802a878c44438da64ff89d4b8754492ae342b1849285e3",
		muunKey:              "5ebf2750bdc86e49e589ddee207eab6638672e1e9d037a5461ae5fdc702f8621",
		tweak:                KeySpendOnlyTweak(),
		msg:                  "8c664cb4843e6670d128469e3c5f53a131cd92939ba49efc015ae4d9db487e1b",
		userSessionId:        "eb8a7990a594347bbfbc1fc208230ba77e5aeb57968298ca989f278b675069ea",
		muunSessionId:        "cc9f909537f3ce40c7292a461910cf2133e3e2b1dbeee3cabe0964edffa45f2e",
		combinedPub:          "3aeb9ee01226a3e50f421d1472a064c5a0537034cc1770174834d8df44d4bac6",
		userNonce:            "02bdd624154b6b11a167a26aef832cfbd6d9d0fbeb2a6b1ef230502c92065474a00367af25653aa9f55e6b0a8b78824dd25a0af7945ebe4ed3da1d1b0f86b0f0fd54",
		muunNonce:            "022bdb069642c36ea5eb8f6ee4af5dafa602f5a1c4e87c1364dec9481d8769c7cd03940111b4ab28cbf1920360c80a4861eaabc0fbdf443ccb3f5e91c0ea95753ba4",
		muunPartialSignature: "29ccc19ad440e90dd03e3011652af94a780e5ee9d5d2cb647fac0d8723f8f7e8",
		fullSignature:        "3bbf83075b8df8bfc1f493d9e51ed0eb22f419d22a3df6f9434fd666be9bacaf893b43fe38eff9c73c66f0525d2bc8b7a96cfd1ad5e145b97ed2e5a9813c53af",
	}, {
		name:                 "sanity 7 (v040)",
		version:              Musig2v040Muun,
		userKey:              "938743adf2d94f83342f28109dd146423c287fdada42a1bfc94cd257dce10e0d",
		muunKey:              "29ef6014b97d3d6c2a9bf93395a245735137733b080913b59f53e1755636c5e2",
		tweak:                KeySpendOnlyTweak(),
		msg:                  "6fe7c58ad896b25f1c86c26bfdb85524bc4ecc87fc38acd0a16448415db4845e",
		userSessionId:        "16ace9f016dd36d59b33576105eaf0b441ad2550bb8aa53940b0886dff30741c",
		muunSessionId:        "31e79edbd0849023181218d1573c67235c6f9f7b7ad88fb6b0ea817a5af22817",
		combinedPub:          "3e33b66b01724807173ca68b264fc211f875501296606ee5699aed7cbf2732eb",
		userNonce:            "0283c130da2c27cde19073b7386ecd34fa297341427a6c190fa53b3efdd8e4d2c40378d05fcc62cfe78ace614e8fbab1124b141b635efe99e4ff686f40fa2b4c6b9d",
		muunNonce:            "0227fbea16aa0f54c5d4b4115edb9ff0d76ce7c808bea2705ee0d9bbe6fd4de19c0236e16ae2604e9d1f0ccb3943b7d1564a3dce418ba2d5a83ae4555347703079d2",
		muunPartialSignature: "c67cc164dde0b8a80208b220761c04a20490ec64ae5e4a7ec400cde8afa20fc0",
		fullSignature:        "c1b7b742705fa6888f44d045bb1a3436c234f6de42e26a4e3e3eb2682812e769198af2f2652b59aa00f0a6e102387c870105031e5ad714d91831501e89c2ae28",
	}, {
		name:                 "sanity 8 (v100)",
		version:              Musig2v100,
		userKey:              "c96ed4f9da26be21374dce108bbe915541b1bfa47efc017980c4320dcb1d5a39",
		muunKey:              "60eb8658f768f9be266c69a181f4dc201ade73909232985127e2c10d6b11ded4",
		tweak:                TapScriptTweak(hexDecode("3f024acdf7597c4ee7b6344a6ff8c8e08b77acd2fe6e42c72d199eebb2c997b1")),
		msg:                  "f5eed1ed2561713a07f9b2e4889b0abd84cf0ff20783b4db952ac52162235763",
		userSessionId:        "8d586a38b6d3c9662faffae3b4cf9ede137bb4aba1e582b0cb4cf13a88e041ad",
		muunSessionId:        "03b942e5de91597f05c2ce50d44131c67936cad0af2fe67d8fce90a8cae0b925",
		combinedPub:          "02b26c54bbeb897bed5e171109eaa765b980b0e8d94cb8a8924660d40a4a77c2b8",
		userNonce:            "02f73c3c63c659f9020444f09134f1a665b9ea4f945333266bcf480e05a5a73f1203936e2303f9f3b99cf43b66c736aa48fcfa6d88cc239e89e52250527d169bab96",
		muunNonce:            "03ab48e4612c3225f661b0a8d8f45753711c7de7e8ef2769e5130f391a7db75a1303556d0914aca06cd479f284ccbfcaf41a6165a773af10b99e421a5c7f36ed5a89",
		muunPartialSignature: "5d719ef048d33ac14c6a0011f1dd6f4693f2d0597d5dd340068f53a2b898d8f5",
		fullSignature:        "a508ef2e4e8fa795537c4e04fb62f548ec05b0f16c97732949904661554b52584ea8a5f7e2f4336f5679744d88205482092844ffc99282e1e1084411d2b7a714",
	}, {
		name:                 "sanity 9 (v100)",
		version:              Musig2v100,
		userKey:              "f163af2ffd707882721f4f594fe9e5c09a5de9af45a5b9c9d81c873d3cbf3439",
		muunKey:              "e5b41cee7d37e82262678145ad6ac8f664506a7c43d67f73cc3a1471d28c93f4",
		tweak:                TapScriptTweak(hexDecode("53b0a68bd9224772ad17966c6c1d055dc47dc7450d065e61e9a65050ce48e85e")),
		msg:                  "3ca3b9bb8e531664e8b25c638673a679b82f15c26f3b52d368d99f8c089ffd9a",
		userSessionId:        "1e81f6dc4801c1df619d817d366055cc5e15a51ff46aabd563df4b956855fa40",
		muunSessionId:        "98364c25dca3a8cec1ec59a9baf825863b208e532ce71336b0e14b63289523ba",
		combinedPub:          "030b6113091eb6aedd1a12aa5b6a58f2bf4c24d5e22944be9d6d2e89fc3187469e",
		userNonce:            "0324761e824500b1798c1d2af367b6229c70ab96aa328fc89f9a4a3ddbc6b96d9503bb3b54ba779641c0700dbe1a8f5e72f9afdc7bfe329599ac5bb2c7b212953316",
		muunNonce:            "03309e05f403e2ee36c7c05975a7fea411a2ba169e4d32366bdb8931cc3d45e42a02d34f33695c07af5f76a476556ad3aed40c66a8c31af70c6fd77fd058e065d58c",
		muunPartialSignature: "a3eea0cdf3d9564e354892c6090473ebdfb92611a488c0173ecbe53331734f55",
		fullSignature:        "3de568db8a74734c8823a106566cb13d00273668ac2dec68df8e80c492d678e293da485ec944e4fff9e94248665f9455d1a901132037395f413c7199a9fcd9b8",
	}, {
		name:                 "sanity 10 (v040)",
		version:              Musig2v040Muun,
		userKey:              "a4de0154616f5e9a8d1beb77bca6a6c4ee5a3fc0d0edb966a1ee7f5e1f7318af",
		muunKey:              "43889a9efda32bf3f39c364b3d373ba4f4b450da6e4ec7b920cf189127300e9b",
		tweak:                KeySpendOnlyTweak(),
		msg:                  "c7d15407975a8a3ed8686b607ee955880745289fedde01c5bdfb4a933c73f98f",
		userSessionId:        "40c4aad751f1e50bf013623dd13fef7285b83155688287fe2f00b00cfcda62b1",
		muunSessionId:        "77df399174557b3a04db01fc9209efe2379b7750ac1cc0cb3fadb2e0632b77ca",
		combinedPub:          "53a65378992d5c521f9390d5716ca6402e2933047aab907a78de6066400adf0b",
		combinedPubUntweaked: "69fd98b014388378f286239e8115444d8a6ad77aeef8d3a4ac63d27cf395a473",
		userNonce:            "0287e08a04a2b96cdebdb59d88b34ea0e39dde004f40fe995408f0c5b423ed8cb0025b2b0d15298ad8cdb64af32849e27e4e628403188c571cfd7b0b1bda06fabc39",
		muunNonce:            "03b0514f0c78e8d6c85781e1f6c63a14add69bb6dce00827c3eacfe8599970cdc1032c8892752ac8ac2b444af84fa58263e3cb4d8301987c25d068249f554401efcd",
		muunPartialSignature: "fab9805aef289b0fb1a5b7e4285920f709fe9552b51633825c548140aff7283e",
		fullSignature:        "e50774777d17470b26631d6ab748d09f8632c56f2b3b29f2f4f81b7f5359d2814752ec522569f1409185cbf118c420219750ab0e2f0138413b28be7913ad9a23",
	}, {
		name:                 "sanity 10 (v100)",
		version:              Musig2v100,
		userKey:              "a4de0154616f5e9a8d1beb77bca6a6c4ee5a3fc0d0edb966a1ee7f5e1f7318af",
		muunKey:              "43889a9efda32bf3f39c364b3d373ba4f4b450da6e4ec7b920cf189127300e9b",
		tweak:                KeySpendOnlyTweak(),
		msg:                  "c7d15407975a8a3ed8686b607ee955880745289fedde01c5bdfb4a933c73f98f",
		userSessionId:        "40c4aad751f1e50bf013623dd13fef7285b83155688287fe2f00b00cfcda62b1",
		muunSessionId:        "77df399174557b3a04db01fc9209efe2379b7750ac1cc0cb3fadb2e0632b77ca",
		combinedPub:          "03a5277614222885d7e9c9096483447785999ed9f1ad7eb5fd008917fecaeebf57",
		combinedPubUntweaked: "035b5ba78d7db091e64827f4f5fa24a644182ce2a3e7cf63720667ca86fc7d7a04",
		userNonce:            "026ec2cc8947d5b8a04e0d06d1fc6c603db250eb204a3ca6f9a88af3a08944767602c9919f05bc49d0d710dda3ac5409d66b82c47f9565c0779faea0420b6e6aefeb",
		muunNonce:            "0217abf0457b4d2b933ed13d6f9afdf3817883642f7ad99665a4a1ec2889743cf4020416bc7dac258f2ce9e37961465f0f8554f73127d72f82458b93e0663899bc1c",
		muunPartialSignature: "094ef219a2613edd393a656a800a24550926a091cb3c0c70522c0978b50d231f",
		fullSignature:        "c74f86e54264c8c4f5c88251ece57b0ee6e318dc03d48bb41f60955c7779c03183631112bfd5514645efe183e7b5e25c156a8edbc8fcc8830acb7eab0c3a83a3",
	}, {
		name:                 "sanity 10 (v100) unhardened /1/2",
		version:              Musig2v100,
		userKey:              "a4de0154616f5e9a8d1beb77bca6a6c4ee5a3fc0d0edb966a1ee7f5e1f7318af",
		muunKey:              "43889a9efda32bf3f39c364b3d373ba4f4b450da6e4ec7b920cf189127300e9b",
		tweak:                KeySpendOnlyTweak().WithUnhardenedDerivationPath([]uint32{1, 2}),
		msg:                  "c7d15407975a8a3ed8686b607ee955880745289fedde01c5bdfb4a933c73f98f",
		userSessionId:        "40c4aad751f1e50bf013623dd13fef7285b83155688287fe2f00b00cfcda62b1",
		muunSessionId:        "77df399174557b3a04db01fc9209efe2379b7750ac1cc0cb3fadb2e0632b77ca",
		combinedPub:          "03a5277614222885d7e9c9096483447785999ed9f1ad7eb5fd008917fecaeebf57",
		combinedPubUntweaked: "035b5ba78d7db091e64827f4f5fa24a644182ce2a3e7cf63720667ca86fc7d7a04",
		userNonce:            "026ec2cc8947d5b8a04e0d06d1fc6c603db250eb204a3ca6f9a88af3a08944767602c9919f05bc49d0d710dda3ac5409d66b82c47f9565c0779faea0420b6e6aefeb",
		muunNonce:            "0217abf0457b4d2b933ed13d6f9afdf3817883642f7ad99665a4a1ec2889743cf4020416bc7dac258f2ce9e37961465f0f8554f73127d72f82458b93e0663899bc1c",
		muunPartialSignature: "094ef219a2613edd393a656a800a24550926a091cb3c0c70522c0978b50d231f",
		fullSignature:        "c74f86e54264c8c4f5c88251ece57b0ee6e318dc03d48bb41f60955c7779c03183631112bfd5514645efe183e7b5e25c156a8edbc8fcc8830acb7eab0c3a83a3",
	}}

	for _, tc := range testCases {
		tc := tc
		t.Run(tc.name, func(tt *testing.T) {
			tt.Parallel()

			// parse params
			var err error
			userKey := hexDecode(tc.userKey)
			muunKey := hexDecode(tc.muunKey)
			msg := hexDecode(tc.msg)
			muunSessionId := hexDecode(tc.muunSessionId)
			userSessionId := hexDecode(tc.userSessionId)

			userPrivateKey := secp256k1.PrivKeyFromBytes(userKey)
			userPublicKeyBytes := SerializePublicKey(tc.version, userPrivateKey.PubKey())

			muunPrivateKey := secp256k1.PrivKeyFromBytes(muunKey)
			muunPublicKeyBytes := SerializePublicKey(tc.version, muunPrivateKey.PubKey())

			// test aggregateKey
			aggregateKey, err := Musig2CombinePubKeysWithTweak(
				tc.version,
				[][]byte{userPublicKeyBytes, muunPublicKeyBytes},
				tc.tweak,
			)
			if err != nil {
				if tc.expectedErr != "" {
					require.ErrorContains(tt, err, tc.expectedErr)
				} else {
					tt.Fatal("Error aggregating keys", err)
				}
				return
			}
			aggregateKeyBytes := SerializePublicKey(tc.version, aggregateKey.FinalKey)
			require.Equal(tt, tc.combinedPub, hex.EncodeToString(aggregateKeyBytes))

			if len(tc.combinedPubUntweaked) > 0 {
				aggregateKeyUntweakedBytes := SerializePublicKey(tc.version, aggregateKey.PreTweakedKey)
				require.Equal(tt, tc.combinedPubUntweaked, hex.EncodeToString(aggregateKeyUntweakedBytes))
			}

			// test user nonce
			userNonce, err := MuSig2GenerateNonce(tc.version, userSessionId, userPublicKeyBytes)
			if err != nil {
				if tc.expectedErr != "" {
					require.ErrorContains(tt, err, tc.expectedErr)
				} else {
					tt.Fatal("Error in user nonce", err)
				}
				return
			}
			require.Equal(tt, tc.userNonce, hex.EncodeToString(userNonce.PubNonce[:]))

			// test muun nonce
			muunNonce, err := MuSig2GenerateNonce(tc.version, muunSessionId, muunPublicKeyBytes)
			if err != nil {
				if tc.expectedErr != "" {
					require.ErrorContains(tt, err, tc.expectedErr)
				} else {
					tt.Fatal("Error in muun nonce", err)
				}
				return
			}
			require.Equal(tt, tc.muunNonce, hex.EncodeToString(muunNonce.PubNonce[:]))

			// test muun partial signature
			muunPartialSignatureBytes, err := ComputeMuunPartialSignature(
				tc.version,
				msg,
				userPublicKeyBytes,
				muunKey,
				userNonce.PubNonce[:],
				muunSessionId,
				tc.tweak,
			)
			if err != nil {
				if tc.expectedErr != "" {
					require.ErrorContains(tt, err, tc.expectedErr)
				} else {
					tt.Fatal("Error in partial signature", err)
				}
				return
			}
			require.Equal(tt, tc.muunPartialSignature, hex.EncodeToString(muunPartialSignatureBytes))

			// finish 2-of-2 signature
			sig, err := ComputeUserPartialSignature(
				tc.version,
				msg,
				userKey,
				muunPublicKeyBytes,
				muunPartialSignatureBytes,
				muunNonce.PubNonce[:],
				userSessionId,
				tc.tweak,
			)
			if err != nil {
				if tc.expectedErr != "" {
					require.ErrorContains(tt, err, tc.expectedErr)
				} else {
					tt.Fatal("Error in full signature. ", err)
				}
				return
			}

			require.Equal(tt, tc.fullSignature, hex.EncodeToString(sig))

			valid, err := VerifySignature(tc.version, msg, aggregateKeyBytes, sig)
			if err != nil {
				if tc.expectedErr != "" {
					require.ErrorContains(tt, err, tc.expectedErr)
				} else {
					tt.Fatal("Error in validation", err)
				}
				return
			}
			require.Equal(tt, true, valid)

			require.NoError(tt, err)
		})
	}
}

func combinePubKeysWithTweak(userPublicKey, muunPublicKey *secp256k1.PublicKey, tweak *MuSig2Tweaks) (*secp256k1.PublicKey, error) {
	pubKeys := [][]byte{
		userPublicKey.SerializeCompressed(),
		muunPublicKey.SerializeCompressed(),
	}

	aggregateKey, err := Musig2CombinePubKeysWithTweak(Musig2v040Muun, pubKeys, tweak)
	if err != nil {
		return nil, fmt.Errorf("Error combining keys: %w", err)
	}

	return aggregateKey.FinalKey, nil
}

func TestSigning(t *testing.T) {
	toSign := [32]byte{1, 2, 3}

	userPriv, _ := btcec.NewPrivateKey()
	muunPriv, _ := btcec.NewPrivateKey()

	tweak := KeySpendOnlyTweak()

	combined, err := combinePubKeysWithTweak(userPriv.PubKey(), muunPriv.PubKey(), tweak)
	if err != nil {
		t.Fatal(err)
	}

	userSessionId := RandomSessionId()
	muunSessionId := RandomSessionId()

	userPubNonces, _ := MuSig2GenerateNonce(Musig2v040Muun, userSessionId[:], nil)
	muunPubNonces, _ := MuSig2GenerateNonce(Musig2v040Muun, muunSessionId[:], nil)

	muunSig, err := ComputeMuunPartialSignature(
		Musig2v040Muun,
		toSign[:],
		userPriv.PubKey().SerializeCompressed(),
		muunPriv.Serialize(),
		userPubNonces.PubNonce[:],
		muunSessionId[:],
		tweak,
	)
	if err != nil {
		t.Fatal(err)
	}

	fullSig, err := ComputeUserPartialSignature(
		Musig2v040Muun,
		toSign[:],
		userPriv.Serialize(),
		muunPriv.PubKey().SerializeCompressed(),
		muunSig,
		muunPubNonces.PubNonce[:],
		userSessionId[:],
		tweak,
	)
	if err != nil {
		t.Fatal(err)
	}

	valid, err := VerifySignature(Musig2v040Muun, toSign[:], combined.SerializeCompressed(), fullSig[:])
	if err != nil {
		t.Fatal(err)
	}
	if !valid {
		t.Fatal("failed to verify sig")
	}
}

func TestCrossWithJava(t *testing.T) {
	decode32Bytes := func(str string) (result [32]byte) {
		d, _ := hex.DecodeString(str)
		copy(result[:], d)
		return
	}

	rawUserPriv := decode32Bytes("6e39c6add6323a5ac5f65e50231fb815026476e734eb9f4f66dce3298fddf1dc")
	rawMuunPriv := decode32Bytes("b876ecf97c19588cf4be95ddc0b06c0d9f623f2cf679276c25e4dfb512b19743")
	userSessionId := decode32Bytes("52fdfc072182654f163f5f0f9a621d729566c74d10037c4d7bbb0407d1e2c649")
	muunSessionId := decode32Bytes("81855ad8681d0d86d1e91e00167939cb6694d2c422acd208a0072939487f6999")
	toSign := decode32Bytes("0102030000000000000000000000000000000000000000000000000000000000")

	expectedKey, _ := hex.DecodeString("027ca7eab04c2ad445418fa6a0ed2a331f121444aedd043adef94bdc00040ff96c")
	expectedSig, _ := hex.DecodeString("773ad923eb5eef593095a6787b674675de2a558335dc3e44fe40cf7b3736637e66d508e4eaadca28dab02e2fdcf5707392b561fffa1837205d2fa77b74cbc82f")

	userPriv, userPub := btcec.PrivKeyFromBytes(rawUserPriv[:])
	muunPriv, muunPub := btcec.PrivKeyFromBytes(rawMuunPriv[:])
	tweak := KeySpendOnlyTweak()

	fmt.Printf("userpub  %x", userPub.SerializeCompressed())
	fmt.Printf("muunpub  %x", muunPub.SerializeCompressed())

	combined, err := combinePubKeysWithTweak(userPub, muunPub, tweak)
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(combined.SerializeCompressed(), expectedKey) {
		t.Fatal("Combined key doesn't match")
	}
	userPubNonces, _ := MuSig2GenerateNonce(Musig2v040Muun, userSessionId[:], nil)
	muunPubNonces, _ := MuSig2GenerateNonce(Musig2v040Muun, muunSessionId[:], nil)

	muunSig, err := ComputeMuunPartialSignature(
		Musig2v040Muun,
		toSign[:],
		userPriv.PubKey().SerializeCompressed(),
		muunPriv.Serialize(),
		userPubNonces.PubNonce[:],
		muunSessionId[:],
		tweak,
	)
	if err != nil {
		t.Fatal(err)
	}

	fullSig, err := ComputeUserPartialSignature(
		Musig2v040Muun,
		toSign[:],
		userPriv.Serialize(),
		muunPriv.PubKey().SerializeCompressed(),
		muunSig,
		muunPubNonces.PubNonce[:],
		userSessionId[:],
		tweak,
	)
	if err != nil {
		t.Fatal(err)
	}

	valid, err := VerifySignature(Musig2v040Muun, toSign[:], combined.SerializeCompressed(), fullSig[:])
	if err != nil {
		t.Fatal(err)
	}
	if !valid {
		t.Fatal("failed to verify sig")
	}
	if !bytes.Equal(fullSig[:], expectedSig) {
		t.Fatal("Signatures do no match")
	}
}
