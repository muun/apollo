use plonky2::field::extension::Extendable;
use plonky2::hash::hash_types::RichField;
use plonky2::plonk::circuit_builder::CircuitBuilder;
use plonky2_bytes::ByteTarget;
use plonky2_bytes::CircuitBuilderBytes;
use plonky2_precomputed_windowed_mul::PrecomputedWindowedMulTarget;

use crate::chacha20_poly1305_bytes::chacha20_poly1305_bytes;
use crate::constants::DEFAULT_PSK;
use crate::constants::DEFAULT_PSK_ID;
use crate::constants::LENGTH_KEY_BYTES;
use crate::constants::LENGTH_NONCE_BYTES;
use crate::constants::MODEL_BASE;
use crate::encoding::SecretKeyTarget;
use crate::kem::encap;
use crate::labeled_hkdf::Usage;
use crate::labeled_hkdf::labeled_expand;
use crate::labeled_hkdf::labeled_extract;
use crate::utils::i2osp;

struct Context {
    key: Vec<ByteTarget>,
    base_nonce: Vec<ByteTarget>,
    seq: usize,
}

impl Context {
    fn increment_seq(&mut self) {
        assert!(self.seq < usize::MAX);
        self.seq += 1;
    }

    fn compute_nonce<F: RichField + Extendable<D>, const D: usize>(
        &mut self,
        builder: &mut CircuitBuilder<F, D>,
    ) -> Vec<ByteTarget> {
        let k = builder.constant_bytes(&i2osp(self.seq, LENGTH_NONCE_BYTES));
        let nonce: Vec<_> = k
            .iter()
            .zip(self.base_nonce.iter())
            .map(|(a, b)| builder.xor(*a, *b))
            .collect();
        self.increment_seq();
        nonce
    }

    fn seal<F: RichField + Extendable<D>, const D: usize>(
        &mut self,
        builder: &mut CircuitBuilder<F, D>,
        aad: &[ByteTarget],
        plaintext: &[ByteTarget],
    ) -> Vec<ByteTarget> {
        let nonce = self.compute_nonce(builder);
        chacha20_poly1305_bytes(builder, &self.key, &nonce, plaintext, aad)
    }

    fn new(key: Vec<ByteTarget>, base_nonce: Vec<ByteTarget>) -> Context {
        Context {
            key,
            base_nonce,
            seq: 0,
        }
    }
}

fn key_schedule<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    shared_secret: &[ByteTarget],
    info: &[ByteTarget],
) -> Context {
    let default_psk_id = builder.constant_bytes(DEFAULT_PSK_ID);
    let default_psk = builder.constant_bytes(DEFAULT_PSK);
    let psk_id_hash = labeled_extract(builder, &[], b"psk_id_hash", &default_psk_id, Usage::Hpke);
    let info_hash = labeled_extract(builder, &[], b"info_hash", info, Usage::Hpke);
    let key_schedule_context = [
        builder.constant_bytes(&i2osp(MODEL_BASE, 1)),
        psk_id_hash,
        info_hash,
    ]
    .concat();
    let secret = labeled_extract(builder, shared_secret, b"secret", &default_psk, Usage::Hpke);
    let key = labeled_expand(
        builder,
        &secret,
        b"key",
        &key_schedule_context,
        LENGTH_KEY_BYTES,
        Usage::Hpke,
    );
    let base_nonce = labeled_expand(
        builder,
        &secret,
        b"base_nonce",
        &key_schedule_context,
        LENGTH_NONCE_BYTES,
        Usage::Hpke,
    );
    Context::new(key, base_nonce)
}

pub fn single_shot<F: RichField + Extendable<D>, const D: usize>(
    builder: &mut CircuitBuilder<F, D>,
    ephemeral_private_key: &SecretKeyTarget,
    receiver_public_key: &PrecomputedWindowedMulTarget,
    info: &[ByteTarget],
    aad: &[ByteTarget],
    plaintext: &[ByteTarget],
) -> (Vec<ByteTarget>, Vec<ByteTarget>) {
    let (shared_secret, enc) = encap(builder, ephemeral_private_key, receiver_public_key);
    let mut context = key_schedule(builder, &shared_secret, info);
    let ciphertext = context.seal(builder, aad, plaintext);
    (ciphertext, enc)
}

#[cfg(test)]
mod test {
    use std::time::SystemTime;

    use plonky2::iop::witness::PartialWitness;
    use plonky2::plonk::circuit_builder::CircuitBuilder;
    use plonky2::plonk::circuit_data::CircuitConfig;
    use plonky2::plonk::config::GenericConfig;
    use plonky2::plonk::config::PoseidonGoldilocksConfig;
    use plonky2_bytes::CircuitBuilderBytes;
    use plonky2_precomputed_windowed_mul::CircuitBuilderPrecomputedWindowedMul;
    use plonky2_precomputed_windowed_mul::from_uncompressed_public_key;

    use crate::encoding::parse_private_key;
    use crate::hpke::single_shot;

    #[derive(Clone)]
    struct TestVector {
        info: &'static str,
        ephemeral_private_key: &'static str,
        ephemeral_public_key: &'static str,
        receiver_public_key: &'static str,
        aad: &'static str,
        plaintext: &'static str,
        ciphertext: &'static str,
    }

    const TEST_VECTORS: &[TestVector] = &[
        TestVector {
            info: "",
            ephemeral_private_key: "312831157576e9e3411d23e5efeb3af16c38927cf6a5e655a6e010ba6f16ca21",
            ephemeral_public_key: "0413f6082ca42883fd226f196f2c60434c416187053e7480a810de825b3118027545d91cec0bf31f76099ec9eb5ff4f30226e6e88e77a01977ac95992c26604bad",
            receiver_public_key: "047d70c6d509a950147a10d251c8ec62a36b1b2ebceaa3e49b2ad13c5e3ae748fc2dd9b79002a196b227a15281ca90689cad26587aabbee6ea6a2d7409548e2556",
            aad: "",
            plaintext: "",
            ciphertext: "85e79a6a327c3af22e6633fcaab1b338",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "178b7e7a32a3ef5e2be6a867c8e44178338b0913f515b3822e80476c75e6ec72",
            ephemeral_public_key: "042438d74563c350d67a847fd13df068b96c0d00dfe94ebccaf6be266ee61660a9c94355e1ebb89205345399f725ed23d6b8cbaa9eb815877d29b55b6061009e0f",
            receiver_public_key: "04007f0d25aa2452bf5e0ace0798f24fb4f1a7eea32135190544bcbe088af283189369b75448f174ab9bcad8b5246fd27f0e65e9aeebd47b62cfe6ce852ffb7c87",
            aad: "",
            plaintext: "cd74c634",
            ciphertext: "63a242ce38ad87543a63b4519dcbe1aa43f32212",
        },
        TestVector {
            info: "ca934a32",
            ephemeral_private_key: "bd02c4d5af0fc7b253f1385d9d433fcfc6cd06de006a2008604625e2a95f70dd",
            ephemeral_public_key: "0436bb51ab8ecfebd7271dd9eca689ef962ee29229499352c25a0d185e6985325a487872d692f12ddca7de4fe47a1163ba828bed5e1aa1989105dbeac31502be77",
            receiver_public_key: "04247c309c15f4fecb7f9826e02a8b2f654f3fca6a6a8c0ce7b31a9734eb60f8b54c224e140d35ff51eb3362ad67d20a0ab4331d7acb4f579f195a99a76737a9fc",
            aad: "",
            plaintext: "",
            ciphertext: "925ab831058881de88c1ca8196e017a3",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "85d410cfab141e67efd22e2b636c3f436a108502dbd3758f3d8f9d6b9e254639",
            ephemeral_public_key: "047fb5eec54f2a4320a4865f64db762d40634a924d2fd164085c056e0c69c50ce08ccb232c2f7f6724636f9e83755f7a58108faa0fd15f198a52aeaef976d8102a",
            receiver_public_key: "0454140ba1ca2fbf1ccff19f52982ae5dc4dbe072449d580e99040851bdce0ed0c1da224ab3de6971068a57d2ed9f689d711fdab184736fcff0bdc8df2e9f95eca",
            aad: "82e5a0d8",
            plaintext: "",
            ciphertext: "1cb5d8635c788983d23a1de82cbcb6af",
        },
        TestVector {
            info: "d11a9ff3",
            ephemeral_private_key: "b1505eab66964efb59dbc75d71b22bb0386ee09bd7a17b619d64215aad91e7e3",
            ephemeral_public_key: "0462fafba814d2aa53c4821c2ae4d4b8d6e58f3b5197a40427607da916b8de0da4d3fd5868e267e4a97f7d62bff66a67ddbbcb60ccfc108fac7a7101b0e0bd3cfb",
            receiver_public_key: "04089ce2f60eced83220aa9d9ae5a51e2c65d12c0faae8ac124fbc896163fe540dfc11febf2041b8c54cd4e1a095806eef97240daabd2c00bc38cd292b86a9f203",
            aad: "",
            plaintext: "1f986ab8",
            ciphertext: "91e65aecf8023ed72857da65a45e3dc5cde1692c",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "71426fcc6129f14f13dcddb426d599aab92e187f542cace2afa4793dfb8e10c5",
            ephemeral_public_key: "040daee78e3f3f9475eca848407adf7126ff658d0e72b074f9ea4eed10e44cd96ace7e9d68419531a8c2e829355e0b3857344b014b7f1222d7f48e24278fea8c1b",
            receiver_public_key: "04af119cb52ddde925a1efbe34bafd93578829546ce26f5063badfc316a1b1cf896eb480b3884c5e1fc5f683d8d6a410b590879ef41bdda12223572edfdea42a95",
            aad: "7af8bee5",
            plaintext: "a0de5787",
            ciphertext: "81b2c3c99ab39e3ef97e06436a2256610a595968",
        },
        TestVector {
            info: "b84fe730",
            ephemeral_private_key: "49577f95464d2b2cd656ea772d07556707c445e5530a7665ecf6b105fd0a7c02",
            ephemeral_public_key: "04383f872c05cc826df22b8332d2d047028b9da0d9c5f6d60dd6842e42486e0fa56e7a6c52fd13d5852059d219a9c9d78747f77c4c15d0b39e044f5f85e72f00c5",
            receiver_public_key: "04d4a7afac9bc25d91025704d961ec0ef662052ba79be6c6743c124969cdf55db964bc2928a740707d3f635d2fdd55569b2635f031c3ce90ec42a19291f0fab17d",
            aad: "2adda296",
            plaintext: "",
            ciphertext: "8148da8f2a94d5c514f53f310607f6dd",
        },
        TestVector {
            info: "c17c038a",
            ephemeral_private_key: "017db271827fd7bf5b18cb6afe5775dab486e0f85c7e195186168044f2b2da14",
            ephemeral_public_key: "04e93862e43634d55d8be3545e042f6057883d27e767852c5db3170faac3c7ade9be43396a893f02d6d32be4991aaaeb17c658eff59efcd1b6ed735f38a315429d",
            receiver_public_key: "045a078bb8174f512299cb672c608724db666bd85356c379815944493774633fdb0ea3585718266a58a106fac5bbffff1b8bd00f1d0b4869fb50f9caab8647e335",
            aad: "b3640228",
            plaintext: "5c2551b2",
            ciphertext: "0ee92c129848d052f91c43261cdb6451311817b7",
        },
        TestVector {
            info: "633668e9d21a341f326ff4",
            ephemeral_private_key: "8075c7f37a4abe3f757160b77199413839c181d455249b3d18929ea69ac57169",
            ephemeral_public_key: "0432d247646b5cbe9d179f39b0786c53dfa69135f89bedaf59e401b27c4af0ccc4f60ec288153b571e697f9468d7e34430b340ae8a1b7d757dd70265c5100f1ad8",
            receiver_public_key: "04e403125b98557061c1f0391394da5d94d0e713ec895a33cda5283f814ff136f1b258c62c13df58b218649d448b3c6bc4737c30977bb296e8d4f7f84a431bfadc",
            aad: "5f38e11af93746cf63eb39",
            plaintext: "1fb0fe1aac2233d983d870",
            ciphertext: "8776e37bb7e787e6036a32a37d071688a4da95166fec37ef51059b",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "ba33ce246d4aa0d0c6cd379796e21d76c118e0b25e5826cdd4504df91d177148",
            ephemeral_public_key: "04f3a60cd312352d5ee1bef6032d1ebac6165c159bcfef327ba61b44e64bc8aba5323aceb5a368963529daf33ea637251ec2ce4230a6f3e34cae3deaeabe03bbea",
            receiver_public_key: "04fa7f3ae9e5a5ccc1aa0fa0ce46fbda4ea4343da7f1432951b76c9cc9b2083d4b524cba2568838a581a7156c0009144c8ff4ff665a7e79df9de727ce293590b50",
            aad: "",
            plaintext: "a3a0c8c3de050dc6944b251e3e71c4096daec34399dc1faa892085fb6bf81f83",
            ciphertext: "5cc7e28b62d5966752a77620abc6775f59f2679354e609f564fb1c80624cf730aa9682f31af7ac9bc18f153c86a91baf",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "c333d5be190b7e75f983f999e25e506595c9e59028dc21454fefdaa3b707a9e8",
            ephemeral_public_key: "043004e898487ea9176ac0441759a8d73f57ee461ca2322171dd2aea98c1ffc67d6a908d704135f596743f22c06c7eabbbdc3e1a28e72257815a74aaef9ed169a8",
            receiver_public_key: "040b054500f986a985ca0974250e5442f4aa1e97f4de2dab32abcad1be00a57f100e6e80627ddd4bb1fb97c5fc37a9be739260b1b39e272ee1d302d0632776849b",
            aad: "",
            plaintext: "95b0aba9c30d24be0cec48cf8620e8855e729d270d913a61041243f91d544d7d",
            ciphertext: "1452c7fabd337d1382ce5fd8bb499271b33377e4688f83ea27f9f5cdeab897d3eb64e286a70ee3d9bcee9f2f00aec1e3",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "02974921b9931b3ebab65b0efa3e70877f32908335aab5a254f15b40af37f225",
            ephemeral_public_key: "04d36fb6064d588c1773efec818cd169c7841634e826a6a24646361c34cf428e6376cda2de8f2d2e2af6ab4719fec4613ae312f30acb589627834880156bbedfee",
            receiver_public_key: "042da0691f401035f0655d3f8e0c7720507163b1b5bcf1b04874140dbbac4c275c3f616c6161af6411bb40eebd97337026d3c30ec131b6dc38ba902a2e563a02eb",
            aad: "",
            plaintext: "a1d994360759bbb7369970729b26b5fc7d727ef194ddfac20dd27f986ddcb86f",
            ciphertext: "2236d82030be176c17e60109edc91ca8ec4fefb0305646f9eba4b68a0763ac6f07a7c20ba4f275c341a0c002dbbe34fc",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "f7901c42f65d8a9053d80aef20c14bf40f070c6730ec9e4d66fb1b241c1b6135",
            ephemeral_public_key: "0460a7217b29e90f2cb866aeccb41ddb71ee5f608ec0bd3ac5133e2f5870cc834cc322646b6f0f10f5b394e0e3079aa387451d7aa5f2906669482808829251b150",
            receiver_public_key: "042b8fb675de2039d6ed3a9987cd5cd41dd525e3f46c35367047e22e3b262a3762af7d914cabe686d209b0b4097a631ec8109adaa3f66e5cb0056af8703c153c13",
            aad: "",
            plaintext: "96f36d3092a53c322b90c010e18a3a811b938501a3e15157225906558bc7d2b6",
            ciphertext: "7687f78f1a747e17a23b8416a03a8ab4fb4df75f093dbf6766d2435ec222fd3c189024141cf7b3893e28d6fc8f66beec",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "5eed25fd8234e6d1b14c903a7ee71eed9618a3b599cb73fb55ec0d5048f1230b",
            ephemeral_public_key: "04ff4e972a56f2d517a11953d77202c5f73bb500c2f973e04f6e17674606e7442723a57e4f349af933c4841df28cab37ac47239bea8221f68ff0855ca2f8c6cbd3",
            receiver_public_key: "0493060302198b4a7f252cb04ba06d71b76529e063dcd4495a6d35f326e4b8d550555df458dd58c4f93f9a78d401f73291e1f5cdc4dae7e988ac732a120916f487",
            aad: "",
            plaintext: "6cd207fe9898c1b10ab1c3e2a03ec454f3af15bbf8af99bec4bb04b4ee2f9a8b",
            ciphertext: "dcc3f64049448df40c1ec4206c951de6461c3f2a3b3a1c3827c6ee0e27f2fc9e83d6685430b7a6d253305e253bf4f72b",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "23bd6e181436cd959cc8c121dd537f547ed0e68e9291f65c0543c159c475197a",
            ephemeral_public_key: "04e6b43ba20f0948392377bc81a89f8649321d5ae2db6201466bc549a739a6ef17568674bfeaacfa131c942b119c59404c3dde03d0b40e7a77795d028bb104a1b6",
            receiver_public_key: "04c7a7f6184dc5afbb23a47a83d7a699ccaf13d7bead45a2e0aa01564f1c96f86ff44c5ee9b4b99e1c9e5e7eb56c01ca964d9a76de63fb9434c95c3ad3b6a9962b",
            aad: "",
            plaintext: "bc6a552a57d73951fd3fb31f3aff5e781ca31372937ef109496dddafba2bbbb5",
            ciphertext: "7675b1c36414abd4f914b5c1c3dfc1e005e2e1e3a61c24ea51fc4044e364d79b661940431f8f89b160da453a2c3e4f35",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "9c59b5de1fe667fe22c27d1ee7bcb074db4a20892c7a0e8351036aac496c09f7",
            ephemeral_public_key: "040d1b6dd05d380f28d66aef322540c309e0b9844ad0752bad683c2ec395d088cf0f4e1b8a248050384e0e6d1dc088cb50d8acd985f21e932a55d51cf09611e4a1",
            receiver_public_key: "040066e5ae05ded9014cdf99a53292aa26d15853fc55de7b262da80b538511e2625fec47d8de4844a5e5ebd22d7d7f8bd1a4e718c1197a73ee48945d3602830f0a",
            aad: "",
            plaintext: "14a8d498bff4707215179032263af6ab6d06609096e73b83b65a2c4972e39899",
            ciphertext: "a2e31d34c0108aba1959f7ca528c72040573a61878fa044f032e3e12f3e198e111c6a3329b58c071def1cefd6dac2325",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "a90f4e8c75ab1051ff3ccc7a30296b761d2556d5d63646f55dc6c1770e1005bf",
            ephemeral_public_key: "0470efeaa07ed4fbc417b061577246baf4e996c47d9de71aa854c548a03dbc0f47aa4460151545016209ea9864f88bb2bc367da5ce3801fcb06fcc364bef0bc412",
            receiver_public_key: "04aa9812225fef2b1be3a19e1bae6fcc77678ea7f6ae8492a80e626aa2409918f860aab4af2711afbab0ee8006867c7b99899d2136d43815da452514c383ff7b57",
            aad: "",
            plaintext: "126ce1ed2f6b3be2f89833cfe539732b8a693fa0a5c247b4f31535c8d9ecb0a2",
            ciphertext: "cf79ea627d1c4e2384da96eb9171d2524d18cc37918d0a562b81224f42691b2e5054b4a75763393ccacb419c26971105",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "eb362422b494ad66cd61794cb030247bb4d54ef37af6d0acf404d6c4f32c5c6c",
            ephemeral_public_key: "04d218e9b6d719763f331114b85d09ab359b23467a9d3c9fb0ff7e37b4b1076ddcb7ab04742b12713790b8bd5c409b31df6e0b0419ae6228551be58e0f906d7788",
            receiver_public_key: "04fd5035cf394b67ad1f8fdde3e55e01315f280e5ba7a0c215a128ef07a762dea2c27df20f5cd648eb4d1b65638967a37a6712cff6b7e21108f3a337589d2bbbac",
            aad: "",
            plaintext: "2a17b1a60aaa323d093ba80a1c25a96dbaf59816acf6e40018b822b773c35ae85522c0aeca81ce74540058e1823ea24488446928ae90ae2e47cdc60baa1ee7f7",
            ciphertext: "9ae72630ecf483fddc287d5b12edf404c615998f94e6c734f762afcd9a906bfc4f212eb62aef84ef34ecb315f9d62417228557fc55c4cbfdfbcb42f280bc6b7681fcd111abf6aa6219c6118568032fb6",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "b7bd2a9de3b6f4186e28628d6e54922a5f15ab8d741756ec4bdfb38686ad061b",
            ephemeral_public_key: "042c12dbfad88c65eb434799217fe4c52296a2c1f602bbbd73e498a9c8333b52c39c8522817727c3c38f08a1708b233ba9be8b1978bb488d13b72e8c0a732b918a",
            receiver_public_key: "04e069ab19a115b01c495d0225a02b9329f666caa2b6314c9e84409ae2c4649c5f093c3ea47c069f57eca8f4a83bdaed405bd5e2ce0336893974c9f5827af4b4e7",
            aad: "",
            plaintext: "df34359c09748f726acb68cc42d70a0714451b837527afbaaf080c072e23f649cbe1e07a1bb06c4deeeba49cbcf2972d9be027aafb84f1fb821127aa246fad2a",
            ciphertext: "284fa6a3281f4e4123652166f329b484efd5a76942ad572612ff000e343cd0e16efdd25bfb415591c79266613ac4d6c1be68d5145edc96719152911b42b59bd290a89fc888d66a62fd70dd6a1fddc33d",
        },
        TestVector {
            info: "",
            ephemeral_private_key: "14963adb83fe678ce0d2ce460d251ec9c040cec8179c6cf9533028beb96e0015",
            ephemeral_public_key: "049a1d756aaef5f307e71d6f43744c65dbd7652202de9c739156aa5a9143e53e5987c75257616f5798fa0264f823b8ac288eddfbeb921949ea10fb069f8debe2d1",
            receiver_public_key: "046e8be355741816790a67121c4eff73d01f88674632a16869ac19663d7748c2194e091e3d0b01e461b0157f8808bfd5183caf6f4802eb78eaa0038c8debc3a32a",
            aad: "",
            plaintext: "df2f8ff2d02b5f183d0cb2a4eb9dc065cffb8f98eefe3352efb85b771aefd3b7f0039c171faf8182f05d8249c4ac7655a19df746f17fc4ab8b07ec4a8815e296",
            ciphertext: "1083dc277eedf365f6d04b39534b7492b10b66815b9b3f1f21d60281f66788020e250a05f785ce24072fa9562ccdbda7091998c550c9ec130161a961e4ef43b0b601771f27069b3f69a10fac4de00769",
        },
        TestVector {
            info: "f2ada89c05f084d6ff6d0bb49d891bb61cafaeac557f119485308942893ef6ee3248d432b8c88b830d3188b0fbe5dccdee644c041660bd206bd8bff6536df5f9",
            ephemeral_private_key: "1360ae696b49bfaaf8f1c9428e922fb5e83bc936087f83bac104ef95a63de3d7",
            ephemeral_public_key: "0419a3469bedcc045860c05ded5f2d51996c5604e9d08d7b7cd193b6150dc3bc11d1b53ac6626f45ce4270a561cb192b1b5111fe2a084dfd664a5fc895ca2931e5",
            receiver_public_key: "04e47d918b7ee6a4dca57a6589d8774be8eb1ae1c66fe3ab66a9e9de651f39cd0506d83f7bc60fd27ca51fc08b15be9bbe574d4e34d6efbadb259a622d1b470c5d",
            aad: "",
            plaintext: "c9e6bc16cd4d373d9960e5a846ac7f986dc46efe59650a94cdd625a6c1a46eee",
            ciphertext: "8401e7ec8f9990284aebcb2ab3d3f7e2de9731cf93682c6fc09e5164c3d9056c5aa811b427cc562c15c19fe8fa584cdd",
        },
        TestVector {
            info: "2b8a49b8e7b669541d9bd450377bc7e0e6ce78eeb4d5378d3a13bc757aa308e6dca9cddce2c814fd2f2c669998d5700485d1674138922605ae48a8c74a3ac307",
            ephemeral_private_key: "b6b197b3819dbcb9eb2aaa8093ba23ef6930807cc31c36dc85e200bc86358405",
            ephemeral_public_key: "040128089f040e93ab26b5e53cb2c7f49d5092e35cc42e9b115fbbdfffc5cf3dbc106799c8f9a9c41b398e70fd29cc8f109fe6cf2591316bfafbc5f1e19fac63b4",
            receiver_public_key: "04ffe14faacfae346d56a9ef2a5e323878eaba13184bdc3b597856febab885f1f509d009890474ad5c188d1f88ca01ad5d6cfde055b87b120d4a5d262d2fa339ba",
            aad: "",
            plaintext: "8eb938552b5bdf217b6543b1a0954422dca7820cd5f2acc18a63495da3e1d4725456784a08ee6944e5b19d659d6b18f3deec04f76e6093fab32b851489c40efd",
            ciphertext: "c31cd35942824f5f4efdf58c5d43ce0e69ebf233cce36829c85b1310fbc35b7af29020b778b384cc241f246471e22d943f4fc909fe2071321bc7677613f5caa6a684c4cfb400314f907868df0c455d5d",
        },
        TestVector {
            info: "e5c5502209a924fcdefa267c0d63a7452fd31433474de6afa24da7be1bee88bb",
            ephemeral_private_key: "aa4701a7a6f5baf4878e66db922dab054e8448d158418c561d80e47991c7300b",
            ephemeral_public_key: "041ddad48f3e7946ecb0ebe1947a78dfda63a2a9aed7905458cec4099be111220061994d201d3c57782c270c8b991b8f59a5e55813aad3d3c94fd10ef4d7270b68",
            receiver_public_key: "045916081dd1bfdfc261d5dec008746e9c21cb8e873b09d28522b0a2ac414b678ad0d3914eb12891b600a08fad6fea23b8e20584a44bb1f8627c5bcb6e0aa68f52",
            aad: "182235678cc891d0f0a0e0277dee0f9e5fa4b8db19af1bf303f9feed27ac68e4",
            plaintext: "eafdffbd7c18f60e405c48c937d6d5cba8d2209a9901f0d3ae8b8a322299b188",
            ciphertext: "de0b206205d45eafcde353e6d368bdd0b0647aa408ee85f338957e1faf283f48a17ee60793f6722d44757ef29f019e68",
        },
        TestVector {
            info: "4836f0950c75024165019d39cae557b58c5d61894e1d73b09e9ef4ce503b087b",
            ephemeral_private_key: "5a93f2e625762a02863c77f1fa59acfd24bf66b29bea6ea7d88fe9ce052f4f5d",
            ephemeral_public_key: "04d6c53ee257aab8f662b478fed9c265d698d4495d93b7f322f16d95e37cbd73ad0683c4107f2c820715d2a974b9463f400fe6d05ff613367c6454a6b24c5faae2",
            receiver_public_key: "0446b160eb4488cfe2c855fc428ad195d629dcc7d5d4c7f302986b95aa6681df13269af8f3771c2293a249143ea08c2a6bc233fa3147e01c79fe1eca6a68cff61d",
            aad: "9cda53314ecab5654ff3e79ef5511151632e5e4ca95b41d42700385b63eccb89",
            plaintext: "290826fbdb4b730cac36619764c62a223eac47f8f650f0926a4919de95854ed0",
            ciphertext: "6555587a2e679108e78587f6181eb050530be6485fb7afa6afba17607e551e5b9509036129cc280c7ed42a88527555f5",
        },
        TestVector {
            info: "966b002be4100e097f0177266b6fc88d8b6a4648620bfa32a17c327345271f5b",
            ephemeral_private_key: "cb9fbcf8b3bf1c3e65a7c80f403ea0d0f7842864658b6c68397d920143e6ea25",
            ephemeral_public_key: "049bfd488cf1f24c778317420bd2dc3c3531c4b77bc43e83ecf173532b2fabfc63faccce79c5786f45c7268ec3a806f84afb0d0fb2ea000c15c8a0e98b76bb283e",
            receiver_public_key: "048ca7e2da0d30a09f6e6407aba360742e9156aa41016e419f4ba50f8d8af82b925fb6b87a8836a63b022beca6fa9b6b85add7bc97c1e885eff8b6f5432df0e327",
            aad: "e4fa78001c18cfd50d43a37961a7f64df7d7f5c705a99e05f418ce4a9ae604b9",
            plaintext: "c6fb1627c6ce05331ca211d6f8c0b4cbe622fef966c31fe9f9336fad950ba206",
            ciphertext: "9f5ccccb6d11284356b6baed82e5f7bda6b38e990228f010c0120998f3ee784ea145f5c89f8fc733df69a51ef760d08d",
        },
        TestVector {
            info: "506560ac823229c047dd28c89f986fdd056f1d62d5075972d307ed04d5e64f0c",
            ephemeral_private_key: "bc349ab3645a843f9bd9f1ac1041b1e35afd1555b43140c4fcf4638779a548c1",
            ephemeral_public_key: "044344711d86d9054ef30ec388fce8fc0a8dfa1de5b412aef565527bb491f894a6d2203bc30d65e67a2380219897c093a4d130a5e46eb1f01993de3ab2c058735f",
            receiver_public_key: "040a914d0592f8c38bc543343c0dafabb8ea5ab0c28bcbe3b8b1d053e0edab8c86bf9179b5579be42ce85ccfcbf8b017e17ef558bc658e1e5dd540ae2fc29bfdd9",
            aad: "ab88c16174eaabd1c42a193931943516b3c56778bb641bedff42fbbaf151ba26",
            plaintext: "ec5da2f66b9eda6d138a3a92795f12217a0c7c1cf0b349550da78b90ef53d507",
            ciphertext: "f938ca03d8c1e78583481733074210aec1fa9e4da2909da2246cabc24ec23d57ed9a9ce53e1822db03339a0945f120bd",
        },
    ];

    #[test]
    fn test() -> anyhow::Result<()> {
        env_logger::builder()
            .is_test(true) // this sets default to debug + disables timestamps
            .try_init()
            .ok();

        let skip_extensive_tests = match std::env::var("SKIP_EXTENSIVE_TESTS") {
            Ok(value) => value == "true",
            Err(_) => false,
        };

        let test_vectors = match skip_extensive_tests {
            true => &[TEST_VECTORS[7].clone()],
            false => TEST_VECTORS,
        };

        for (i, test_vector) in test_vectors.iter().enumerate() {
            let mut now = SystemTime::now();

            let info = hex::decode(test_vector.info)?;
            let ephemeral_private_key = hex::decode(test_vector.ephemeral_private_key)?;
            let ephemeral_public_key = hex::decode(test_vector.ephemeral_public_key)?;
            let receiver_public_key = hex::decode(test_vector.receiver_public_key)?;
            let aad = hex::decode(test_vector.aad)?;
            let plaintext = hex::decode(test_vector.plaintext)?;
            let expected_ciphertext = hex::decode(test_vector.ciphertext)?;

            const D: usize = 2;
            type C = PoseidonGoldilocksConfig;
            type F = <C as GenericConfig<D>>::F;

            let mut builder = CircuitBuilder::<F, D>::new(CircuitConfig {
                num_wires: 190,
                ..CircuitConfig::standard_recursion_config()
            });

            let ephemeral_private_key = builder.constant_bytes(&ephemeral_private_key);
            let ephemeral_public_key = builder.constant_bytes(&ephemeral_public_key);
            let info = builder.constant_bytes(&info);
            let aad = builder.constant_bytes(&aad);
            let plaintext = builder.constant_bytes(&plaintext);
            let expected_ciphertext = builder.constant_bytes(&expected_ciphertext);

            let ephemeral_private_key = parse_private_key(&mut builder, &ephemeral_private_key);
            let receiver_public_key_precomputation = builder
                .add_const_precomputed_windowed_mul_target(from_uncompressed_public_key(
                    receiver_public_key.try_into().unwrap(),
                ));

            let (ciphertext, enc) = single_shot(
                &mut builder,
                &ephemeral_private_key,
                &receiver_public_key_precomputation,
                &info,
                &aad,
                &plaintext,
            );

            builder.connect_bytes(&enc, &ephemeral_public_key);

            builder.connect_bytes(&ciphertext, &expected_ciphertext);

            let data = builder.build::<C>();

            eprintln!(
                "Built circuit for test vector {} in {} seconds",
                i,
                now.elapsed()?.as_secs_f32(),
            );
            now = SystemTime::now();

            let proof = data.prove(PartialWitness::new())?;
            data.verify(proof)?;

            eprintln!(
                "Test vector {} passed, proving time: {} seconds",
                i,
                now.elapsed()?.as_secs_f32(),
            );
        }
        Ok(())
    }
}
