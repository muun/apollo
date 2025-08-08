package bitcoin_hpke

import (
	"encoding/hex"
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/test-go/testify/assert"
	"testing"
)

type testVector struct {
	info                        string
	ephemeralPrivateKey         string
	ephemeralPublicKey          string
	receiverPrivateKey          string
	receiverPublicKey           string
	sharedSecret                string
	key                         string
	baseNonce                   string
	exporterSecret              string
	additionalAuthenticatedData string
	plaintext                   string
	ciphertext                  string
}

func TestHpke(t *testing.T) {
	for _, v := range testVectors {
		plaintext := MustDecode(t, v.plaintext)
		receiverPublicKey := MustParsePublicKey(t, v.receiverPublicKey)
		info := MustDecode(t, v.info)
		additionalAuthenticatedData := MustDecode(t, v.additionalAuthenticatedData)

		testingOnlyGenerateKeyPair = func() (*btcec.PrivateKey, *btcec.PublicKey, error) {
			ephemeralPrivateKey := MustParsePrivateKey(t, v.ephemeralPrivateKey)
			return ephemeralPrivateKey, ephemeralPrivateKey.PubKey(), nil
		}

		encryptedMessage, err := SingleShotEncrypt(plaintext, receiverPublicKey, info, additionalAuthenticatedData)
		if err != nil {
			t.Fatal(err)
		}
		assert.Equal(t, MustParsePublicKey(t, v.ephemeralPublicKey), encryptedMessage.GetEncapsulatedKey())
		assert.Equal(t, MustDecode(t, v.ciphertext), encryptedMessage.GetCiphertext())

		decryptedPlaintext, err := encryptedMessage.SingleShotDecrypt(MustParsePrivateKey(t, v.receiverPrivateKey),
			info,
			additionalAuthenticatedData,
		)
		if err != nil {
			t.Fatal(err)
		}
		assert.Equal(t, plaintext, decryptedPlaintext)

	}
}

func TestSerializationAndParsing(t *testing.T) {
	for _, v := range testVectors {
		plaintext := MustDecode(t, v.plaintext)
		receiverPublicKey := MustParsePublicKey(t, v.receiverPublicKey)
		info := MustDecode(t, v.info)
		additionalAuthenticatedData := MustDecode(t, v.additionalAuthenticatedData)

		testingOnlyGenerateKeyPair = nil

		encryptedMessage, err := SingleShotEncrypt(plaintext, receiverPublicKey, info, additionalAuthenticatedData)
		if err != nil {
			t.Fatal(err)
		}

		serializedMessage := encryptedMessage.Serialize()
		deserializedMessage, err := ParseEncryptedMessage(serializedMessage)
		if err != nil {
			t.Fatal(err)
		}
		assert.Equal(t, encryptedMessage.GetEncapsulatedKey(), deserializedMessage.GetEncapsulatedKey())
		assert.Equal(t, encryptedMessage.GetCiphertext(), deserializedMessage.GetCiphertext())

		assert.Equal(t, len(serializedMessage), SerializedEncryptedMessageLengthInBytes(len(plaintext)))
	}
}

func MustParsePrivateKey(t *testing.T, s string) *btcec.PrivateKey {
	privateKey, _ := btcec.PrivKeyFromBytes(MustDecode(t, s))
	return privateKey
}

func MustParsePublicKey(t *testing.T, s string) *btcec.PublicKey {
	publicKey, err := btcec.ParsePubKey(MustDecode(t, s))
	if err != nil {
		t.Fatal(err)
	}
	return publicKey
}

func MustDecode(t *testing.T, s string) []byte {
	bs, err := hex.DecodeString(s)
	if err != nil {
		t.Fatal(err)
	}
	return bs
}

var testVectors = [26]testVector{
	{
		info:                        "",
		ephemeralPrivateKey:         "312831157576e9e3411d23e5efeb3af16c38927cf6a5e655a6e010ba6f16ca21",
		ephemeralPublicKey:          "0413f6082ca42883fd226f196f2c60434c416187053e7480a810de825b3118027545d91cec0bf31f76099ec9eb5ff4f30226e6e88e77a01977ac95992c26604bad",
		receiverPrivateKey:          "09bbefb55e56548922b6d35de0b95344b8d569415f4c90f15d1441f1cd1bca4b",
		receiverPublicKey:           "047d70c6d509a950147a10d251c8ec62a36b1b2ebceaa3e49b2ad13c5e3ae748fc2dd9b79002a196b227a15281ca90689cad26587aabbee6ea6a2d7409548e2556",
		sharedSecret:                "d7275fcaab68699a8ef6854f3a6e85e1b0314a108927d04e1f4961a538d3e3b9",
		key:                         "94ea9705ec513308aa423a5542712b132791c15696fe62036936f0c56deafd83",
		baseNonce:                   "91f4df09602026724fc9ab11",
		exporterSecret:              "c05cb0915f0e07bfb205e737f97f7bd86c1f0db08f67efc29a55396356ebabbb",
		additionalAuthenticatedData: "",
		plaintext:                   "",
		ciphertext:                  "85e79a6a327c3af22e6633fcaab1b338",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "178b7e7a32a3ef5e2be6a867c8e44178338b0913f515b3822e80476c75e6ec72",
		ephemeralPublicKey:          "042438d74563c350d67a847fd13df068b96c0d00dfe94ebccaf6be266ee61660a9c94355e1ebb89205345399f725ed23d6b8cbaa9eb815877d29b55b6061009e0f",
		receiverPrivateKey:          "7f0b8fe19589ce2b9f156ebab49d6d03976d0969f1b872d75d4385bdb65b9dda",
		receiverPublicKey:           "04007f0d25aa2452bf5e0ace0798f24fb4f1a7eea32135190544bcbe088af283189369b75448f174ab9bcad8b5246fd27f0e65e9aeebd47b62cfe6ce852ffb7c87",
		sharedSecret:                "7d153129057ba148425a0751579f935d59aa6bcb8ee9eedb8820c66b3bca0a66",
		key:                         "8898f8b84ab9e07e53975ee8cdd24990a8bb6b6a275f9e7f2ea2f1285757788b",
		baseNonce:                   "d3cf2312277bda8f8cd190dd",
		exporterSecret:              "578ce8f69cf247d2c77921ac5ac794af12852d11239ac39ba27826ac0e639467",
		additionalAuthenticatedData: "",
		plaintext:                   "cd74c634",
		ciphertext:                  "63a242ce38ad87543a63b4519dcbe1aa43f32212",
	},
	{
		info:                        "ca934a32",
		ephemeralPrivateKey:         "bd02c4d5af0fc7b253f1385d9d433fcfc6cd06de006a2008604625e2a95f70dd",
		ephemeralPublicKey:          "0436bb51ab8ecfebd7271dd9eca689ef962ee29229499352c25a0d185e6985325a487872d692f12ddca7de4fe47a1163ba828bed5e1aa1989105dbeac31502be77",
		receiverPrivateKey:          "c7fa0c07592ede040d65ac865a4bf9627e1e4527bffb0bf22b1cbd49c7b9e9ac",
		receiverPublicKey:           "04247c309c15f4fecb7f9826e02a8b2f654f3fca6a6a8c0ce7b31a9734eb60f8b54c224e140d35ff51eb3362ad67d20a0ab4331d7acb4f579f195a99a76737a9fc",
		sharedSecret:                "a13c42352dae44602118d9a8d44237df7e749b3bef24e17d06213cd114829c6c",
		key:                         "39f0a43f0787c62d16cf3b92b3c009ec9ecf50c154d263e85aab5ad1faf6678f",
		baseNonce:                   "37a89715dab54ac09c291927",
		exporterSecret:              "4b4325a2682d8fe70583e9e309752de2d20dd7ca7a70c9202677fcdc15e1e461",
		additionalAuthenticatedData: "",
		plaintext:                   "",
		ciphertext:                  "925ab831058881de88c1ca8196e017a3",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "85d410cfab141e67efd22e2b636c3f436a108502dbd3758f3d8f9d6b9e254639",
		ephemeralPublicKey:          "047fb5eec54f2a4320a4865f64db762d40634a924d2fd164085c056e0c69c50ce08ccb232c2f7f6724636f9e83755f7a58108faa0fd15f198a52aeaef976d8102a",
		receiverPrivateKey:          "60d51b81e7990e688e5c6d0887479130176a79cc111c9dd881125bb00d82914e",
		receiverPublicKey:           "0454140ba1ca2fbf1ccff19f52982ae5dc4dbe072449d580e99040851bdce0ed0c1da224ab3de6971068a57d2ed9f689d711fdab184736fcff0bdc8df2e9f95eca",
		sharedSecret:                "fd64f22b9586e022d5ef428e6de0ae7c290e298cdb517de93289b1c1b6a45469",
		key:                         "a0b873e490b7cb6f213466d61ed45b609d14183eae5940924a0b8c66cb0af0ba",
		baseNonce:                   "c3e5d91ef98bf99c16ba21e7",
		exporterSecret:              "c1e0606a9eddb4e12c11b1bec61d274c92476008b451b7f5490abc994d60fd37",
		additionalAuthenticatedData: "82e5a0d8",
		plaintext:                   "",
		ciphertext:                  "1cb5d8635c788983d23a1de82cbcb6af",
	},
	{
		info:                        "d11a9ff3",
		ephemeralPrivateKey:         "b1505eab66964efb59dbc75d71b22bb0386ee09bd7a17b619d64215aad91e7e3",
		ephemeralPublicKey:          "0462fafba814d2aa53c4821c2ae4d4b8d6e58f3b5197a40427607da916b8de0da4d3fd5868e267e4a97f7d62bff66a67ddbbcb60ccfc108fac7a7101b0e0bd3cfb",
		receiverPrivateKey:          "b8a34a85bbbab82ea8edec50a4e199f7f4832f8eb6ae54c54e08b6fb501eb15c",
		receiverPublicKey:           "04089ce2f60eced83220aa9d9ae5a51e2c65d12c0faae8ac124fbc896163fe540dfc11febf2041b8c54cd4e1a095806eef97240daabd2c00bc38cd292b86a9f203",
		sharedSecret:                "08e927181387a2003ca3cee03c81e4168bf5ff4b6ead4d2e6ef15d74b3346fcf",
		key:                         "36d29a808dd7d58e569fc16e00ac7c02d8078455b8e2a6560886bf4d23cabbac",
		baseNonce:                   "d586c0eff42c28e2a6d5a844",
		exporterSecret:              "7a20d7b2125e999233217391a5e9505bd554b90326ce8cc6a982320e8ff9f217",
		additionalAuthenticatedData: "",
		plaintext:                   "1f986ab8",
		ciphertext:                  "91e65aecf8023ed72857da65a45e3dc5cde1692c",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "71426fcc6129f14f13dcddb426d599aab92e187f542cace2afa4793dfb8e10c5",
		ephemeralPublicKey:          "040daee78e3f3f9475eca848407adf7126ff658d0e72b074f9ea4eed10e44cd96ace7e9d68419531a8c2e829355e0b3857344b014b7f1222d7f48e24278fea8c1b",
		receiverPrivateKey:          "ab279e1ac3ba41ce8942a5566676d7fb18f37e23bf177daa2af8754c0e6d86aa",
		receiverPublicKey:           "04af119cb52ddde925a1efbe34bafd93578829546ce26f5063badfc316a1b1cf896eb480b3884c5e1fc5f683d8d6a410b590879ef41bdda12223572edfdea42a95",
		sharedSecret:                "99767ee79703841e68234933cba2159b681530edeec18b7219ba6078d0df25d2",
		key:                         "b600aac8d4b2ce21f92606afe5aef05f35fcb96e637fd20ebe959d30f4e24cc1",
		baseNonce:                   "b6a47a057674130e415ceddc",
		exporterSecret:              "1f9b057db90c981bc0898017778898102e36a2574c7ab459d8ece4f8f61e0e4d",
		additionalAuthenticatedData: "7af8bee5",
		plaintext:                   "a0de5787",
		ciphertext:                  "81b2c3c99ab39e3ef97e06436a2256610a595968",
	},
	{
		info:                        "b84fe730",
		ephemeralPrivateKey:         "49577f95464d2b2cd656ea772d07556707c445e5530a7665ecf6b105fd0a7c02",
		ephemeralPublicKey:          "04383f872c05cc826df22b8332d2d047028b9da0d9c5f6d60dd6842e42486e0fa56e7a6c52fd13d5852059d219a9c9d78747f77c4c15d0b39e044f5f85e72f00c5",
		receiverPrivateKey:          "4ab33a7ab4673cc887238cd741c908f38b40099f9947c4a8d85c7a3d0be57367",
		receiverPublicKey:           "04d4a7afac9bc25d91025704d961ec0ef662052ba79be6c6743c124969cdf55db964bc2928a740707d3f635d2fdd55569b2635f031c3ce90ec42a19291f0fab17d",
		sharedSecret:                "89ac497106280e57106808fbd32568764a54548beb1f52641a83651b14d72f8f",
		key:                         "fc84ed1298f9b977d99977267c15e9630f183ba4a233e9e61d083f4957b8107c",
		baseNonce:                   "f9e87f894b5d6281b9847177",
		exporterSecret:              "fd86cf08e5c08da28636516ad0311f26a9d92fcc0ecc5dd4ecdc8a2a32dfd267",
		additionalAuthenticatedData: "2adda296",
		plaintext:                   "",
		ciphertext:                  "8148da8f2a94d5c514f53f310607f6dd",
	},
	{
		info:                        "c17c038a",
		ephemeralPrivateKey:         "017db271827fd7bf5b18cb6afe5775dab486e0f85c7e195186168044f2b2da14",
		ephemeralPublicKey:          "04e93862e43634d55d8be3545e042f6057883d27e767852c5db3170faac3c7ade9be43396a893f02d6d32be4991aaaeb17c658eff59efcd1b6ed735f38a315429d",
		receiverPrivateKey:          "797cfdce7a8d6ab93b89f572352301c4c981ce0ae8555662b8eca474948e8c40",
		receiverPublicKey:           "045a078bb8174f512299cb672c608724db666bd85356c379815944493774633fdb0ea3585718266a58a106fac5bbffff1b8bd00f1d0b4869fb50f9caab8647e335",
		sharedSecret:                "679757957437b0b8289cab3cfba401ba37c40ae90d398144c0b514b083825f64",
		key:                         "82414b85201a27c53a1d7b33fcdc75ac13c8e0c53dd0baef8705284cd0be011e",
		baseNonce:                   "91c8ed9a89617672b7f77c1c",
		exporterSecret:              "b66b08dd2b0c672f24752ab1a37c662aaddb64c11f9fba79a7a2440b6eb5e2d5",
		additionalAuthenticatedData: "b3640228",
		plaintext:                   "5c2551b2",
		ciphertext:                  "0ee92c129848d052f91c43261cdb6451311817b7",
	},
	{
		info:                        "633668e9d21a341f326ff4",
		ephemeralPrivateKey:         "8075c7f37a4abe3f757160b77199413839c181d455249b3d18929ea69ac57169",
		ephemeralPublicKey:          "0432d247646b5cbe9d179f39b0786c53dfa69135f89bedaf59e401b27c4af0ccc4f60ec288153b571e697f9468d7e34430b340ae8a1b7d757dd70265c5100f1ad8",
		receiverPrivateKey:          "2861accc5fbb1bf87426a20955f19c5a3d1082b3d99bbdc6372edb06a085a3cf",
		receiverPublicKey:           "04e403125b98557061c1f0391394da5d94d0e713ec895a33cda5283f814ff136f1b258c62c13df58b218649d448b3c6bc4737c30977bb296e8d4f7f84a431bfadc",
		sharedSecret:                "bf5e7436e7e494bb2b1fdf595313fe23756b9f2104e2b3f64fa749f0577e2a2c",
		key:                         "76ef85c3e2ad000728ad76cbb38fc97ed8f44af45ceba9bef8b7068713953b5b",
		baseNonce:                   "ffbe77f07e95485dc65b49a8",
		exporterSecret:              "9a5c236bc263759e0492525d9122eadb0823fa4ed094f312da7813074abbfe55",
		additionalAuthenticatedData: "5f38e11af93746cf63eb39",
		plaintext:                   "1fb0fe1aac2233d983d870",
		ciphertext:                  "8776e37bb7e787e6036a32a37d071688a4da95166fec37ef51059b",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "ba33ce246d4aa0d0c6cd379796e21d76c118e0b25e5826cdd4504df91d177148",
		ephemeralPublicKey:          "04f3a60cd312352d5ee1bef6032d1ebac6165c159bcfef327ba61b44e64bc8aba5323aceb5a368963529daf33ea637251ec2ce4230a6f3e34cae3deaeabe03bbea",
		receiverPrivateKey:          "447258b81582187d82617454bc17671349b481bab7396d96100b2e68386ac477",
		receiverPublicKey:           "04fa7f3ae9e5a5ccc1aa0fa0ce46fbda4ea4343da7f1432951b76c9cc9b2083d4b524cba2568838a581a7156c0009144c8ff4ff665a7e79df9de727ce293590b50",
		sharedSecret:                "6034a8e5525450b7f0bd4574584f46be7da245bee410902e5942ea287e5a5c7d",
		key:                         "ef0a0b2d3e653e11826c35a4375b90dc79c2730a85add38fb9b32054f4b933d9",
		baseNonce:                   "e487074e37175d69e2a25c8c",
		exporterSecret:              "abc84eca4ad564d3650d8a6a4553cda521ffc7c890cb21e0960fe15d0b165b5e",
		additionalAuthenticatedData: "",
		plaintext:                   "a3a0c8c3de050dc6944b251e3e71c4096daec34399dc1faa892085fb6bf81f83",
		ciphertext:                  "5cc7e28b62d5966752a77620abc6775f59f2679354e609f564fb1c80624cf730aa9682f31af7ac9bc18f153c86a91baf",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "c333d5be190b7e75f983f999e25e506595c9e59028dc21454fefdaa3b707a9e8",
		ephemeralPublicKey:          "043004e898487ea9176ac0441759a8d73f57ee461ca2322171dd2aea98c1ffc67d6a908d704135f596743f22c06c7eabbbdc3e1a28e72257815a74aaef9ed169a8",
		receiverPrivateKey:          "29ccb0f073d2565be10d4a3a038749c99fcae948996bb47d3b3b7841e82e7cfe",
		receiverPublicKey:           "040b054500f986a985ca0974250e5442f4aa1e97f4de2dab32abcad1be00a57f100e6e80627ddd4bb1fb97c5fc37a9be739260b1b39e272ee1d302d0632776849b",
		sharedSecret:                "59a7675b77f5da3be24c95e9a3286e71e211cdf1a48c10aeb52b96c0bf6fbde1",
		key:                         "0e4cacfb661a2beb3f15b16d3277611072608dffd443dcb89472d757f2938db6",
		baseNonce:                   "04256550a7570074ee8bda41",
		exporterSecret:              "ee12956018bc1c1f1f4e94efa551dda3c0b69c82979bd3a2e8ded03369090c5b",
		additionalAuthenticatedData: "",
		plaintext:                   "95b0aba9c30d24be0cec48cf8620e8855e729d270d913a61041243f91d544d7d",
		ciphertext:                  "1452c7fabd337d1382ce5fd8bb499271b33377e4688f83ea27f9f5cdeab897d3eb64e286a70ee3d9bcee9f2f00aec1e3",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "02974921b9931b3ebab65b0efa3e70877f32908335aab5a254f15b40af37f225",
		ephemeralPublicKey:          "04d36fb6064d588c1773efec818cd169c7841634e826a6a24646361c34cf428e6376cda2de8f2d2e2af6ab4719fec4613ae312f30acb589627834880156bbedfee",
		receiverPrivateKey:          "611e34c87150c2d528aa1931289fe52e3071e1cab067a0aee676ce77550c85bf",
		receiverPublicKey:           "042da0691f401035f0655d3f8e0c7720507163b1b5bcf1b04874140dbbac4c275c3f616c6161af6411bb40eebd97337026d3c30ec131b6dc38ba902a2e563a02eb",
		sharedSecret:                "d62a09bb6bf6de3b78d900228ca16d7c60f70dab567015f5cbe2333f2e0dba4a",
		key:                         "0af33bd2e36a4aa22a0d30da334ffd5c416fc633be5951ade99942fff07ad204",
		baseNonce:                   "4640d5917c273817d9dc77b2",
		exporterSecret:              "34a0cd1ea06c5770dc50cf6ce2a23e21467257dfcfecdbcf0936fc011886f0ea",
		additionalAuthenticatedData: "",
		plaintext:                   "a1d994360759bbb7369970729b26b5fc7d727ef194ddfac20dd27f986ddcb86f",
		ciphertext:                  "2236d82030be176c17e60109edc91ca8ec4fefb0305646f9eba4b68a0763ac6f07a7c20ba4f275c341a0c002dbbe34fc",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "f7901c42f65d8a9053d80aef20c14bf40f070c6730ec9e4d66fb1b241c1b6135",
		ephemeralPublicKey:          "0460a7217b29e90f2cb866aeccb41ddb71ee5f608ec0bd3ac5133e2f5870cc834cc322646b6f0f10f5b394e0e3079aa387451d7aa5f2906669482808829251b150",
		receiverPrivateKey:          "048fc556a900b966a303abbb6bd7a71e30b2285fad4efecb8d3a50f6fe770c41",
		receiverPublicKey:           "042b8fb675de2039d6ed3a9987cd5cd41dd525e3f46c35367047e22e3b262a3762af7d914cabe686d209b0b4097a631ec8109adaa3f66e5cb0056af8703c153c13",
		sharedSecret:                "a61026a6f767ad62c8f6ad248af00cb7b63acf8567e1ee03a7b77257cf2aba5a",
		key:                         "4b523bd5a2407781c2a7a9ba111a268a88414be04792b2539cb5cc9121d46bb3",
		baseNonce:                   "37611acc2b00bc9633043ff6",
		exporterSecret:              "e822c9174222978cc73d8e0de96d33931fd1050224cc13f3d527967361aa964f",
		additionalAuthenticatedData: "",
		plaintext:                   "96f36d3092a53c322b90c010e18a3a811b938501a3e15157225906558bc7d2b6",
		ciphertext:                  "7687f78f1a747e17a23b8416a03a8ab4fb4df75f093dbf6766d2435ec222fd3c189024141cf7b3893e28d6fc8f66beec",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "5eed25fd8234e6d1b14c903a7ee71eed9618a3b599cb73fb55ec0d5048f1230b",
		ephemeralPublicKey:          "04ff4e972a56f2d517a11953d77202c5f73bb500c2f973e04f6e17674606e7442723a57e4f349af933c4841df28cab37ac47239bea8221f68ff0855ca2f8c6cbd3",
		receiverPrivateKey:          "4e93f0c94d845ba5b84fa9c23df914ea07335e92f24136eab34689297c21bca4",
		receiverPublicKey:           "0493060302198b4a7f252cb04ba06d71b76529e063dcd4495a6d35f326e4b8d550555df458dd58c4f93f9a78d401f73291e1f5cdc4dae7e988ac732a120916f487",
		sharedSecret:                "1ae575561597f61ba34e75944d48351a57443c0613ad918084517eced2907d1d",
		key:                         "a5d8f1576b845f7ea37e98c7d28d9c95913130f934d91ea38ab74426c76fc690",
		baseNonce:                   "b24b8eb9d05c066148e8815f",
		exporterSecret:              "9a10f685358c832fe571c969643b48f28f7de9e08f8f5581179e9065b7397e37",
		additionalAuthenticatedData: "",
		plaintext:                   "6cd207fe9898c1b10ab1c3e2a03ec454f3af15bbf8af99bec4bb04b4ee2f9a8b",
		ciphertext:                  "dcc3f64049448df40c1ec4206c951de6461c3f2a3b3a1c3827c6ee0e27f2fc9e83d6685430b7a6d253305e253bf4f72b",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "23bd6e181436cd959cc8c121dd537f547ed0e68e9291f65c0543c159c475197a",
		ephemeralPublicKey:          "04e6b43ba20f0948392377bc81a89f8649321d5ae2db6201466bc549a739a6ef17568674bfeaacfa131c942b119c59404c3dde03d0b40e7a77795d028bb104a1b6",
		receiverPrivateKey:          "5b12974c6b9d6dd6c3d795628dc2d8679eebfc885cc2cdb508b108f1f44e3898",
		receiverPublicKey:           "04c7a7f6184dc5afbb23a47a83d7a699ccaf13d7bead45a2e0aa01564f1c96f86ff44c5ee9b4b99e1c9e5e7eb56c01ca964d9a76de63fb9434c95c3ad3b6a9962b",
		sharedSecret:                "62908e076f95bbb6a7b1f115a7187de26753cb7804fcabbcf5e5828928ba79e5",
		key:                         "c5b9391d706a2e727af6dd7c2d4f54b41a08e3d74225e05e494ca628de65cbf6",
		baseNonce:                   "8d9a3d99006b02b7907a1ceb",
		exporterSecret:              "b6d28df42d48e1e795ccd61eeae954df74fe6a1c245d632f532ef46966addba4",
		additionalAuthenticatedData: "",
		plaintext:                   "bc6a552a57d73951fd3fb31f3aff5e781ca31372937ef109496dddafba2bbbb5",
		ciphertext:                  "7675b1c36414abd4f914b5c1c3dfc1e005e2e1e3a61c24ea51fc4044e364d79b661940431f8f89b160da453a2c3e4f35",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "9c59b5de1fe667fe22c27d1ee7bcb074db4a20892c7a0e8351036aac496c09f7",
		ephemeralPublicKey:          "040d1b6dd05d380f28d66aef322540c309e0b9844ad0752bad683c2ec395d088cf0f4e1b8a248050384e0e6d1dc088cb50d8acd985f21e932a55d51cf09611e4a1",
		receiverPrivateKey:          "9e27565e428270b14b163fa004ca1b6fae96a3d0255433078d96150da0bd89df",
		receiverPublicKey:           "040066e5ae05ded9014cdf99a53292aa26d15853fc55de7b262da80b538511e2625fec47d8de4844a5e5ebd22d7d7f8bd1a4e718c1197a73ee48945d3602830f0a",
		sharedSecret:                "6783533c159e143c8dc67ccbcedb9ff059ea4c05fd294d817459797186f38c01",
		key:                         "450e6b36ccb8592ff64ec1d9cdaf3f70c8e9116e5ee5fc6382890ad2f6729e22",
		baseNonce:                   "d2834b3709b30a726b2fd57f",
		exporterSecret:              "c280ea68d5e389a269efc21c1b5990b8ab8fb4e0f6549b0887438a42b23acaa7",
		additionalAuthenticatedData: "",
		plaintext:                   "14a8d498bff4707215179032263af6ab6d06609096e73b83b65a2c4972e39899",
		ciphertext:                  "a2e31d34c0108aba1959f7ca528c72040573a61878fa044f032e3e12f3e198e111c6a3329b58c071def1cefd6dac2325",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "a90f4e8c75ab1051ff3ccc7a30296b761d2556d5d63646f55dc6c1770e1005bf",
		ephemeralPublicKey:          "0470efeaa07ed4fbc417b061577246baf4e996c47d9de71aa854c548a03dbc0f47aa4460151545016209ea9864f88bb2bc367da5ce3801fcb06fcc364bef0bc412",
		receiverPrivateKey:          "2a2938d163d40fc4352d19d95a84652be47606671e9afc22d0e879aff1a593ec",
		receiverPublicKey:           "04aa9812225fef2b1be3a19e1bae6fcc77678ea7f6ae8492a80e626aa2409918f860aab4af2711afbab0ee8006867c7b99899d2136d43815da452514c383ff7b57",
		sharedSecret:                "d8dffa3f8d08fbc3a687ae297d0a9eb530906fa5765517eb5b443a653a1e1c21",
		key:                         "37566adca5e7e6e0eaafe75e5125abee00682ae41e2a6aec42821367f743d9be",
		baseNonce:                   "6b64aaedc61523af88ae106c",
		exporterSecret:              "544cd9a2ffacb006684dddee294fbfa0bde06ca9856c76e211d045fbd53799d6",
		additionalAuthenticatedData: "",
		plaintext:                   "126ce1ed2f6b3be2f89833cfe539732b8a693fa0a5c247b4f31535c8d9ecb0a2",
		ciphertext:                  "cf79ea627d1c4e2384da96eb9171d2524d18cc37918d0a562b81224f42691b2e5054b4a75763393ccacb419c26971105",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "eb362422b494ad66cd61794cb030247bb4d54ef37af6d0acf404d6c4f32c5c6c",
		ephemeralPublicKey:          "04d218e9b6d719763f331114b85d09ab359b23467a9d3c9fb0ff7e37b4b1076ddcb7ab04742b12713790b8bd5c409b31df6e0b0419ae6228551be58e0f906d7788",
		receiverPrivateKey:          "f9e68368fe8502baefb9a5f61c3ebbb0741ee2977dd3271f548eb8010ba248fd",
		receiverPublicKey:           "04fd5035cf394b67ad1f8fdde3e55e01315f280e5ba7a0c215a128ef07a762dea2c27df20f5cd648eb4d1b65638967a37a6712cff6b7e21108f3a337589d2bbbac",
		sharedSecret:                "65da2004e05fe604c1d628b34059d3bda3b068206cafb9271d77b6b2f4663f43",
		key:                         "556c1f582704927141cb326c8247f571db3c5cc27d08cc737068986a7ab36a02",
		baseNonce:                   "5314d49399dade867ac4e740",
		exporterSecret:              "aba0f243c8421941f068aa776c481a4eebbe7dafd4a4d248942ad9fb2708f620",
		additionalAuthenticatedData: "",
		plaintext:                   "2a17b1a60aaa323d093ba80a1c25a96dbaf59816acf6e40018b822b773c35ae85522c0aeca81ce74540058e1823ea24488446928ae90ae2e47cdc60baa1ee7f7",
		ciphertext:                  "9ae72630ecf483fddc287d5b12edf404c615998f94e6c734f762afcd9a906bfc4f212eb62aef84ef34ecb315f9d62417228557fc55c4cbfdfbcb42f280bc6b7681fcd111abf6aa6219c6118568032fb6",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "b7bd2a9de3b6f4186e28628d6e54922a5f15ab8d741756ec4bdfb38686ad061b",
		ephemeralPublicKey:          "042c12dbfad88c65eb434799217fe4c52296a2c1f602bbbd73e498a9c8333b52c39c8522817727c3c38f08a1708b233ba9be8b1978bb488d13b72e8c0a732b918a",
		receiverPrivateKey:          "1b563a60e2e3d8f8719a857e2f4cc4f001c080dfce92c340094ad82816f77d8d",
		receiverPublicKey:           "04e069ab19a115b01c495d0225a02b9329f666caa2b6314c9e84409ae2c4649c5f093c3ea47c069f57eca8f4a83bdaed405bd5e2ce0336893974c9f5827af4b4e7",
		sharedSecret:                "af7237ab04ed59f989283943c99012d58ad2230fbc25ef5be6ba70d419969643",
		key:                         "d848bfc19d7ec25e8d1132f3342d44c93217926976198242c372560a6a5b6760",
		baseNonce:                   "51c4f15c5709ac0ec709e73a",
		exporterSecret:              "0cb82c797b68a6986e6d925c91196106f684c73ff2809d3b7a5548604001e6be",
		additionalAuthenticatedData: "",
		plaintext:                   "df34359c09748f726acb68cc42d70a0714451b837527afbaaf080c072e23f649cbe1e07a1bb06c4deeeba49cbcf2972d9be027aafb84f1fb821127aa246fad2a",
		ciphertext:                  "284fa6a3281f4e4123652166f329b484efd5a76942ad572612ff000e343cd0e16efdd25bfb415591c79266613ac4d6c1be68d5145edc96719152911b42b59bd290a89fc888d66a62fd70dd6a1fddc33d",
	},
	{
		info:                        "",
		ephemeralPrivateKey:         "14963adb83fe678ce0d2ce460d251ec9c040cec8179c6cf9533028beb96e0015",
		ephemeralPublicKey:          "049a1d756aaef5f307e71d6f43744c65dbd7652202de9c739156aa5a9143e53e5987c75257616f5798fa0264f823b8ac288eddfbeb921949ea10fb069f8debe2d1",
		receiverPrivateKey:          "d2f80a353ac4fe24743a50ab3318b10b46086be337dc15b7c177714b81dd64f6",
		receiverPublicKey:           "046e8be355741816790a67121c4eff73d01f88674632a16869ac19663d7748c2194e091e3d0b01e461b0157f8808bfd5183caf6f4802eb78eaa0038c8debc3a32a",
		sharedSecret:                "127b869bc4aa0a3fe23e7a9c7834092bf82719219128ecd468154df54f45a5cb",
		key:                         "d4d29d20e0dc587d105b648813a3982ed05b879e7cee74ff6706f99327ddee9b",
		baseNonce:                   "9156ed532ec43d119b37f73f",
		exporterSecret:              "c42c88b0a7306d4b0d4794f20e728945027021903b79bec5525a05fb1d040893",
		additionalAuthenticatedData: "",
		plaintext:                   "df2f8ff2d02b5f183d0cb2a4eb9dc065cffb8f98eefe3352efb85b771aefd3b7f0039c171faf8182f05d8249c4ac7655a19df746f17fc4ab8b07ec4a8815e296",
		ciphertext:                  "1083dc277eedf365f6d04b39534b7492b10b66815b9b3f1f21d60281f66788020e250a05f785ce24072fa9562ccdbda7091998c550c9ec130161a961e4ef43b0b601771f27069b3f69a10fac4de00769",
	},
	{
		info:                        "f2ada89c05f084d6ff6d0bb49d891bb61cafaeac557f119485308942893ef6ee3248d432b8c88b830d3188b0fbe5dccdee644c041660bd206bd8bff6536df5f9",
		ephemeralPrivateKey:         "1360ae696b49bfaaf8f1c9428e922fb5e83bc936087f83bac104ef95a63de3d7",
		ephemeralPublicKey:          "0419a3469bedcc045860c05ded5f2d51996c5604e9d08d7b7cd193b6150dc3bc11d1b53ac6626f45ce4270a561cb192b1b5111fe2a084dfd664a5fc895ca2931e5",
		receiverPrivateKey:          "097c03cb98c996c059c58e1da8ff685a517dc0f5b2cdf7a3e01a7aff4e05def5",
		receiverPublicKey:           "04e47d918b7ee6a4dca57a6589d8774be8eb1ae1c66fe3ab66a9e9de651f39cd0506d83f7bc60fd27ca51fc08b15be9bbe574d4e34d6efbadb259a622d1b470c5d",
		sharedSecret:                "90889a18da556475f23c91edddfa46a6e22e3ac1a7bf534943ff232db8575544",
		key:                         "16ff002d4fe08290e5cd5c493e1a04baed40f56cc6029c6c05341891bf9e8ae8",
		baseNonce:                   "226b3b76d9939f54f4b99b9d",
		exporterSecret:              "4e78eb7649303f24b7a1a86f0172d40c9744dd1d972c8b1f814896c71c177c25",
		additionalAuthenticatedData: "",
		plaintext:                   "c9e6bc16cd4d373d9960e5a846ac7f986dc46efe59650a94cdd625a6c1a46eee",
		ciphertext:                  "8401e7ec8f9990284aebcb2ab3d3f7e2de9731cf93682c6fc09e5164c3d9056c5aa811b427cc562c15c19fe8fa584cdd",
	},
	{
		info:                        "2b8a49b8e7b669541d9bd450377bc7e0e6ce78eeb4d5378d3a13bc757aa308e6dca9cddce2c814fd2f2c669998d5700485d1674138922605ae48a8c74a3ac307",
		ephemeralPrivateKey:         "b6b197b3819dbcb9eb2aaa8093ba23ef6930807cc31c36dc85e200bc86358405",
		ephemeralPublicKey:          "040128089f040e93ab26b5e53cb2c7f49d5092e35cc42e9b115fbbdfffc5cf3dbc106799c8f9a9c41b398e70fd29cc8f109fe6cf2591316bfafbc5f1e19fac63b4",
		receiverPrivateKey:          "f93b5bb456f0491ba13a20e1b051354922d33b301423a2c770df45a500ab3e74",
		receiverPublicKey:           "04ffe14faacfae346d56a9ef2a5e323878eaba13184bdc3b597856febab885f1f509d009890474ad5c188d1f88ca01ad5d6cfde055b87b120d4a5d262d2fa339ba",
		sharedSecret:                "0f719b11855c598a91b44a7abfbd891125dd60169b8ebc8fa84652529940b57d",
		key:                         "00d6bb1ffba6f13a5db8710f538a7141a104fceb028f0f27a4f1be916b003800",
		baseNonce:                   "0b8a40e93374752d640444ed",
		exporterSecret:              "49a1202ad2fcb94d7e76118559012aece1af1a60c26a4af754baa30d8c307a7e",
		additionalAuthenticatedData: "",
		plaintext:                   "8eb938552b5bdf217b6543b1a0954422dca7820cd5f2acc18a63495da3e1d4725456784a08ee6944e5b19d659d6b18f3deec04f76e6093fab32b851489c40efd",
		ciphertext:                  "c31cd35942824f5f4efdf58c5d43ce0e69ebf233cce36829c85b1310fbc35b7af29020b778b384cc241f246471e22d943f4fc909fe2071321bc7677613f5caa6a684c4cfb400314f907868df0c455d5d",
	},
	{
		info:                        "e5c5502209a924fcdefa267c0d63a7452fd31433474de6afa24da7be1bee88bb",
		ephemeralPrivateKey:         "aa4701a7a6f5baf4878e66db922dab054e8448d158418c561d80e47991c7300b",
		ephemeralPublicKey:          "041ddad48f3e7946ecb0ebe1947a78dfda63a2a9aed7905458cec4099be111220061994d201d3c57782c270c8b991b8f59a5e55813aad3d3c94fd10ef4d7270b68",
		receiverPrivateKey:          "d46f48d94b91f9ec552d5c7611a9b72e0f24e82bf43f56d1b58654dfa858dc50",
		receiverPublicKey:           "045916081dd1bfdfc261d5dec008746e9c21cb8e873b09d28522b0a2ac414b678ad0d3914eb12891b600a08fad6fea23b8e20584a44bb1f8627c5bcb6e0aa68f52",
		sharedSecret:                "4b9ab4ed38a04602d6175d12ed1c804dc2e34433a1c2c250303df87afb95024a",
		key:                         "079737d36f56ab9f005257d8e75f8a43e196ce4912dbd2720b6010ed3c31fe49",
		baseNonce:                   "5b45cf9a6fef1f0013936369",
		exporterSecret:              "d0a92e997d2be5bc6ccc9a68b54e185910220ec8499e410db178d6fd255f20c8",
		additionalAuthenticatedData: "182235678cc891d0f0a0e0277dee0f9e5fa4b8db19af1bf303f9feed27ac68e4",
		plaintext:                   "eafdffbd7c18f60e405c48c937d6d5cba8d2209a9901f0d3ae8b8a322299b188",
		ciphertext:                  "de0b206205d45eafcde353e6d368bdd0b0647aa408ee85f338957e1faf283f48a17ee60793f6722d44757ef29f019e68",
	},
	{
		info:                        "4836f0950c75024165019d39cae557b58c5d61894e1d73b09e9ef4ce503b087b",
		ephemeralPrivateKey:         "5a93f2e625762a02863c77f1fa59acfd24bf66b29bea6ea7d88fe9ce052f4f5d",
		ephemeralPublicKey:          "04d6c53ee257aab8f662b478fed9c265d698d4495d93b7f322f16d95e37cbd73ad0683c4107f2c820715d2a974b9463f400fe6d05ff613367c6454a6b24c5faae2",
		receiverPrivateKey:          "885b6f624fa46e37fe83b64a665256ba5c5b82926eccbeb6c62fd3d9f0439a2d",
		receiverPublicKey:           "0446b160eb4488cfe2c855fc428ad195d629dcc7d5d4c7f302986b95aa6681df13269af8f3771c2293a249143ea08c2a6bc233fa3147e01c79fe1eca6a68cff61d",
		sharedSecret:                "51702c398c0a01b10a8286037e241eece16ac592cd1035d4c667c3a67bc33fae",
		key:                         "01f5e8129a926917c640e5164d024edde32b21d531cf76b65caa513d1fa46f97",
		baseNonce:                   "e1a40e461e91911350ad6c96",
		exporterSecret:              "37eba781daca896042b78fdfc71663462f7b28eb0a4cb8923e14648369a06944",
		additionalAuthenticatedData: "9cda53314ecab5654ff3e79ef5511151632e5e4ca95b41d42700385b63eccb89",
		plaintext:                   "290826fbdb4b730cac36619764c62a223eac47f8f650f0926a4919de95854ed0",
		ciphertext:                  "6555587a2e679108e78587f6181eb050530be6485fb7afa6afba17607e551e5b9509036129cc280c7ed42a88527555f5",
	},
	{
		info:                        "966b002be4100e097f0177266b6fc88d8b6a4648620bfa32a17c327345271f5b",
		ephemeralPrivateKey:         "cb9fbcf8b3bf1c3e65a7c80f403ea0d0f7842864658b6c68397d920143e6ea25",
		ephemeralPublicKey:          "049bfd488cf1f24c778317420bd2dc3c3531c4b77bc43e83ecf173532b2fabfc63faccce79c5786f45c7268ec3a806f84afb0d0fb2ea000c15c8a0e98b76bb283e",
		receiverPrivateKey:          "7288f560a74c5e78e041d671ce2a311c26d9e666351d9d8d0f6b9dc373e45c84",
		receiverPublicKey:           "048ca7e2da0d30a09f6e6407aba360742e9156aa41016e419f4ba50f8d8af82b925fb6b87a8836a63b022beca6fa9b6b85add7bc97c1e885eff8b6f5432df0e327",
		sharedSecret:                "178980764bc30f339cc5f5c068b1ce0c32f5ce4c548a5eb8a6c1ce1c597687da",
		key:                         "84f2677122fa83684b47bf395ce4ff9478d5c92b28625d9c794f0f0834b60c60",
		baseNonce:                   "4c919b87b1e7c90bf0d4dd34",
		exporterSecret:              "6218cf9f6d79fd3c6da23e1e17149766c4735abcdf0a9a1a34475c66c776c4cc",
		additionalAuthenticatedData: "e4fa78001c18cfd50d43a37961a7f64df7d7f5c705a99e05f418ce4a9ae604b9",
		plaintext:                   "c6fb1627c6ce05331ca211d6f8c0b4cbe622fef966c31fe9f9336fad950ba206",
		ciphertext:                  "9f5ccccb6d11284356b6baed82e5f7bda6b38e990228f010c0120998f3ee784ea145f5c89f8fc733df69a51ef760d08d",
	},
	{
		info:                        "506560ac823229c047dd28c89f986fdd056f1d62d5075972d307ed04d5e64f0c",
		ephemeralPrivateKey:         "bc349ab3645a843f9bd9f1ac1041b1e35afd1555b43140c4fcf4638779a548c1",
		ephemeralPublicKey:          "044344711d86d9054ef30ec388fce8fc0a8dfa1de5b412aef565527bb491f894a6d2203bc30d65e67a2380219897c093a4d130a5e46eb1f01993de3ab2c058735f",
		receiverPrivateKey:          "4f7aaac55f1bc139471be00076a6c0b2f2fd4dbea234500e705760c429d56407",
		receiverPublicKey:           "040a914d0592f8c38bc543343c0dafabb8ea5ab0c28bcbe3b8b1d053e0edab8c86bf9179b5579be42ce85ccfcbf8b017e17ef558bc658e1e5dd540ae2fc29bfdd9",
		sharedSecret:                "e9f73f9fd9cf6fdabee76a4a31354d81ed543a92a1e30c84a34b135eef085f5e",
		key:                         "a48ea99eac818b950c2c3aa3b3c74a18b3c68ce929c8be4f8f18b72c6e405ce4",
		baseNonce:                   "1179cfb70ff9bfa396e04d9d",
		exporterSecret:              "b112064f612d79696b317b72996ba4e052c694e1894841a8edb01372c0b3c7bd",
		additionalAuthenticatedData: "ab88c16174eaabd1c42a193931943516b3c56778bb641bedff42fbbaf151ba26",
		plaintext:                   "ec5da2f66b9eda6d138a3a92795f12217a0c7c1cf0b349550da78b90ef53d507",
		ciphertext:                  "f938ca03d8c1e78583481733074210aec1fa9e4da2909da2246cabc24ec23d57ed9a9ce53e1822db03339a0945f120bd",
	},
}
