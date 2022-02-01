/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.proof;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.InnerNodeDiscoveryManager;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.Proof;
import org.hyperledger.besu.ethereum.trie.RemoveVisitor;
import org.hyperledger.besu.ethereum.trie.SimpleMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

import com.google.common.collect.Ordering;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class WorldStateProofProvider {

  private final WorldStateStorage worldStateStorage;

  public static void main(final String[] args) {

    Map<Bytes, Bytes> keys = new HashMap<>();
    keys.put(
        Bytes.fromHexString("0x0175b7a638427703f0dbe7bb9bbf987a2551717b34e79f33b5b1008d1fa01db9"),
        Bytes.fromHexString("0x8461f18188"));
    keys.put(
        Bytes.fromHexString("0x030f0b8263f26a55a99d85b3d078863602da885da62e653404a775bdc54fff0b"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x036b6384b5eca791c62761152d0c79bb0604c104a5fb6f4eb0703f3154bb3db0"),
        Bytes.fromHexString(
            "0xa04752464900000000000000000000000000000000000000000000000000000008"));
    keys.put(
        Bytes.fromHexString("0x03d89cc1852eab11c3abd1005ac7a15a79a25290b8ca3c361ac7c72aa7c38e74"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x03e924ef39ab223115671f1a80dcaa7c6bf160d58eaa538af4268dfa90eaa05f"),
        Bytes.fromHexString("0x8b0626f3aed21427607b568b"));
    keys.put(
        Bytes.fromHexString("0x057c384a7d1c54f3a1b2e5e67b2617b8224fdfd1ea7234eea573a6ff665ff63e"),
        Bytes.fromHexString("0x821388"));
    keys.put(
        Bytes.fromHexString("0x0718d55a4b9fca90fc18edf192ff4e3954cee0d3cac18d6eee6a4d9c000bd88a"),
        Bytes.fromHexString("0x8b132312df7616c5b0815a41"));
    keys.put(
        Bytes.fromHexString("0x09f4d9f0396cb1ab1b9bd88e8ad20010b1843437512736177fb7770b10219ee1"),
        Bytes.fromHexString("0x8b036f1f6c3fd106a512d460"));
    keys.put(
        Bytes.fromHexString("0x0af72bb22ac11ece739895582cedfaca124735cf4ce50221e8fc149dfb694d9f"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0x10b7ae96e1f7c192d5756e3c3dab5e7df09c5eb2ab640d1cbe8ef146474aa56b"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0x144fe5aa4316b41c2ecfc19afb2df5e440da87a870bcf258f786ebf61bf2d397"),
        Bytes.fromHexString("0x8b02d98a878f2c3c78102d70"));
    keys.put(
        Bytes.fromHexString("0x1676d1f395e54d95780783172d36d814ce56af92a4cd3e855137a5da826094ee"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x173363746a7df38584dda22ec6e6f4c55c53e21c8bf6c2be312de59db2ff3b9a"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0x18f6d2dc6db2f0eeb0df8f25c164925d5afad3966632f78b0d8e382953d1084f"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x193fcb54cc36f6cef5e317e878c28e4962ee36360d4432ab4d919d000e6bc133"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0x1994ff098e4b87d01d47ec108b05110a2abd499df96c8e255f2f876b82160ae1"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x1a3f4c0f5ec9ba9367a0e41cbea8b1462f39d60835d61d3e63492e4d7045fc21"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x1a89b95781eca6a26c2de1fc70275d7802577b1a6cd1c77f620321e1c6d53cff"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x1e6c943018c872f5e1aaa113a6b6f1b02fe559b26ebd042026b58bb9683d09a9"),
        Bytes.fromHexString("0x8b017bc4ec2e2129fbf06865"));
    keys.put(
        Bytes.fromHexString("0x1eb39b617f97edf08ea1de5a11f2e3fc530a4b962425179e963fc6102f94f834"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x2305124a051540912a10ad36aaca408c4f45c7b9b6f8727f34f5974b56a0245b"),
        Bytes.fromHexString("0x8b0707f1ec5dcde3d8800000"));
    keys.put(
        Bytes.fromHexString("0x236a2bdea5ac1e8082afd5a1b61087092760c766da13c086492a268383ddbdca"),
        Bytes.fromHexString("0x8b0a8beae28cb4d5c4c00000"));
    keys.put(
        Bytes.fromHexString("0x268b910b9af8376842287dbef14fa240d2e34efa6b1b5870bb6fde99faaba13a"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563"),
        Bytes.fromHexString("0x9409f5b4feb92381e2584cf5dabcf8af5791fb6a98"));
    keys.put(
        Bytes.fromHexString("0x2bbbfb883dce0fd1d37a36e95256d9956e3f0ae1c533855eccfc69a1b174b4cf"),
        Bytes.fromHexString("0x8b0ad045a8653edebf724a5e"));
    keys.put(
        Bytes.fromHexString("0x301b9cc58cb55460454413631c107511d41991c3c59c04c193b212e2ac73624b"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0x32b598f87a6226ec040b96f3f8fe2e89ac9d614d7a62d771aa19149d1e731610"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0x3506598c2e538463d0ec70b75961e66176ee70599c5d53d127ba87ab30e37023"),
        Bytes.fromHexString("0x8b0653f354bad2e6a9400000"));
    keys.put(
        Bytes.fromHexString("0x358911d525b262babcb4cd428162f608363c2e893657b825b2cc0bbb8ec227b9"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x37aeabd0a66797ddf9e6105635e21e83ffe6c444700854155670d2ec36826c0d"),
        Bytes.fromHexString("0x8b0653f354bad2e6a9400000"));
    keys.put(
        Bytes.fromHexString("0x3851100e30a1b19e75f0275e7d4d36acba6aa6226261d96ffe3e1ced3694f39b"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x3ad8aa4f87544323a9d1e5dd902f40c356527a7955687113db5f9a85ad579dc1"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0x3bc419400aa468b2b6a3959e0fb0910659c9225ebc25fa010bd28375a785a8f5"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ace"),
        Bytes.fromHexString("0x82dead"));
    keys.put(
        Bytes.fromHexString("0x47dced9124101fe932b95688275372b2406c84fe784feea0643c3cc94c799dd4"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0x4ab98389fd4b1f30daf10bef9867b1eceae0b8dca35aef093a560f3900b4fbf7"),
        Bytes.fromHexString("0x8b1831093bc42e133906fec1"));
    keys.put(
        Bytes.fromHexString("0x4c7f6a2d1789bdd507f57737b8c76f514f4f1fd443877fc739e5ba9791050ba9"),
        Bytes.fromHexString("0x8b0bfb08f6e182f7dd4f2c54"));
    keys.put(
        Bytes.fromHexString("0x500919bbf9370c26447449796b78f5acf24473c67b4664a67cbeedcbe05525cf"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x51434360b779ca0a3da787403eea40aad4476ebb06462f8f9d793ccf6c8f6f29"),
        Bytes.fromHexString(
            "0xa0fffffffffffffffffffffffffffffffffffffffffff1bc8b1fe42f48978fffff"));
    keys.put(
        Bytes.fromHexString("0x540a3f884c5060e09a10571844a54e378e7fd83c480536006044181495225a03"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x58fd1457cc8b2c185807c6be6e9e032535a46526e80d10909d3b12f60606f45a"),
        Bytes.fromHexString("0x8b0707f1ec7c009851174000"));
    keys.put(
        Bytes.fromHexString("0x597e71eb40e1141b3d886947098b3f6152c852735b8fce816c4b496348d63595"),
        Bytes.fromHexString("0x8b09f5b2251a63d01eda51e5"));
    keys.put(
        Bytes.fromHexString("0x5af323966db51db3e0d4a822edd680f1ebb28456aa5288fb98684250426b9790"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x603848a5a439d401a2f9970f26bad41cd51182c9ce22d752cf4d0836ea7836c4"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0x607f8c8348fc68efb7f998233af76c4e17420f28b52bac4674b2c909a26a2339"),
        Bytes.fromHexString("0x8b02e2e35ef47cb03956b466"));
    keys.put(
        Bytes.fromHexString("0x61436a38237a06e6796ecb94b29278b3002913d0620d5723c66aac7edb23236f"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0x63c0bb55d92ac5d6480f6fe38e6c9754497553da861afb371d7385e87596115b"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0x67a0e5e78559bfd9f07ff8cd742757fed30ad839fed8d3eecb2592f812458158"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0x689398a85b29c51135fa65d378f301ebcb82cc05d16b80e1154cf5ec37cb90e3"),
        Bytes.fromHexString("0x8b1e83c2e83af352b13c98d8"));
    keys.put(
        Bytes.fromHexString("0x69413ceac746d8e9f0a47a9800b465ab1b8b3af9f939305d8b25225108cc868a"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0x6a26c0f8abfe667328a52afb6bedee29ec8c84db94f1da6081159ad8e9d3ef2e"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x6e1540171b6c0c960b71a7020d9f60077f6af931a8bbf590da0223dacf75c7af"),
        Bytes.fromHexString("0x8205dc"));
    keys.put(
        Bytes.fromHexString("0x70383fbfed826254d5418e6135ab3b6a683d41eceacda99e7b9c394ddf370950"),
        Bytes.fromHexString("0x8b0432ac4d1fe99c5de00000"));
    keys.put(
        Bytes.fromHexString("0x714686a8c4818add7af5fe8429b485a85a800f196933174beab87d41b79dd54b"),
        Bytes.fromHexString("0x8aaeb356f102aa71a00000"));
    keys.put(
        Bytes.fromHexString("0x71760ca2f4a7953206f76ad2596da64c68413e757894dd0f946ef56056f3116c"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x722e3df5933f15d355f4119a355ca6e733afe323c395f51b71cfdbfc70443f02"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0x726dff88a0d9489a5913f5a8cc7ef0b8b2e2f929fa9e8e7ecd69aba11dc7dfc3"),
        Bytes.fromHexString("0x8b16a254f1876680773cf835"));
    keys.put(
        Bytes.fromHexString("0x73bbf62f0f28717bdfdfbdb29992f98ee858a0879fb2dd9601c1c4654d447d7e"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x75a319b643c68f94f8862949b8b97e39b3b2dfa4a15901371c9005d0df7119c3"),
        Bytes.fromHexString("0x8b0d7d9c031e25e9fd0af721"));
    keys.put(
        Bytes.fromHexString("0x76be4e318df7ff34447da4a1c8c287ead2de39170ef188c313be59480fc129c9"),
        Bytes.fromHexString("0x95446c3b15f9926687d2c40534fdb563ffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x78e38cbffe47a41721451950bbc463aba373eae398fe562ba542684068dd371e"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x7b49753bc04fa3facc004dd891bd0ba07b034217d60f66dca52c68f1ea9e1397"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x7b674826336370c48f8d517ce77914a2e9dfc00d9a08a420bbb091723518f79f"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x7c1bc1932e63672be0831ccf3507f4b26c2b103b60c800143380ea07725c8405"),
        Bytes.fromHexString("0x8b1bb0b411f79138c56a0bd9"));
    keys.put(
        Bytes.fromHexString("0x7c6f65b31109d0cff64ba2c901ba2f585ba6706dc16f43aeca268b2b36b1ef1d"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0x7ea63758a39ff3c2ff02295a7b5daa2e8ff0d2ecfff3e54fb003a6c5955eb99f"),
        Bytes.fromHexString("0x8b0e0fe3d8bb9bc7b1000000"));
    keys.put(
        Bytes.fromHexString("0x81c1f7a900a1d9ec8d0c8597a76b2b2b30481a3c7f2ce27a53acc6f3ec01644a"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x8409718679983b467b7062593b4a4ae8ab6ee6eaff6eac63560125b1dfada331"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0x878fd4ef76653237e00f92c7eebe828edc9c5c08c3f4bf1e452e3eea21c86c69"),
        Bytes.fromHexString("0x8b09bb2e0396de087d738e94"));
    keys.put(
        Bytes.fromHexString("0x8a35acfbc15ff81a39ae7d344fd709f28e8600b4aa8c65c6b64bfe7fe36bd19b"),
        Bytes.fromHexString(
            "0xa04772696d61636546696e616e636500000000000000000000000000000000001c"));
    keys.put(
        Bytes.fromHexString("0x8d1108e10bcb7c27dddfc02ed9d693a074039d026cf4ea4240b40f7d581ac802"),
        Bytes.fromHexString("0x948373409b76b2fd596f2571cfed6fb3a5a3e111af"));
    keys.put(
        Bytes.fromHexString("0x8edd2199a22fec284784e998ed95287775189b26a971a7567ec32b1f6b9b4741"),
        Bytes.fromHexString("0x8c013905ac84c1632f3e274c25"));
    keys.put(
        Bytes.fromHexString("0x911c4b326fe38ec674982acc644be301083bd5487b2343f67e5ad391e2e43d59"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x912d51f7d46db21db830c4026a6759173834f8ad9f418b6f2bd8cc238890451a"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0x944998273e477b495144fb8794c914197f3ccb46be2900f4698fd0ef743c9695"),
        Bytes.fromHexString("0x821388"));
    keys.put(
        Bytes.fromHexString("0x948d0db17c069125819de12798ec88ae4d63da9b208f7a6f441f472e9ee52ada"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0x9591a83a48976d80c13a860c21e930a506984c552b98ac48412c0b0070d8809e"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x978e278926d5f3a5eda76073e297286b266bba2e29e37efe3e4c6ad2dbf56890"),
        Bytes.fromHexString("0x8802dbdac452eed0f2"));
    keys.put(
        Bytes.fromHexString("0x9861e96b7b2756f56ef5fc1b9bd5a002b75c8649c03737694e3512498cee2a43"),
        Bytes.fromHexString("0x8b0f427d7f2f3376dc62eafb"));
    keys.put(
        Bytes.fromHexString("0x99be976e7aebe58824acab61d47c39254edba0605bb89a5bf9cc40587b94f0f9"),
        Bytes.fromHexString("0x8b06c4163362814d90fed30a"));
    keys.put(
        Bytes.fromHexString("0x9a323f0231a3534b0535fd157569954f2ca6c4300006a1376194d36ba4e7cf8d"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0x9e0f19814b14ebe5876daa7844f04544a53e0d15cc6ffffee4a39f52ebdc0cac"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x9ef6305a6ed2798f06d94e66327592f18cf41ac8d7358274e1e1305062b2b511"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0x9f2f54df307b20f1a20c08ee4d2bb329ac90bb2a9188b7b404aa7b1971589cc4"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xa2d005ab503eb57e5439eb7bf11d6657d597e6dfba0a10070fcbf716475296d6"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0xa369810f4596175f37c2881d38dc9c65f14858c849e6b85c46c61f3595df54f1"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xa66cc928b5edb82af9bd49922954155ab7b0942694bea4ce44661d9a8736c688"),
        Bytes.fromHexString("0x64"));
    keys.put(
        Bytes.fromHexString("0xa72304d0154b3d987a53b3cfc3e5968f43f62c386c9bad1af3860a824bd55121"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xa7844a61957f7085e626970fa91f714fde613ffa6758d031372f61a293ef54ab"),
        Bytes.fromHexString("0x8b0e0fe3d8bb9bc7b1000000"));
    keys.put(
        Bytes.fromHexString("0xa7f6f1014ae719dfe401f0df522066ca1c6ef223ae8171b84dfc95b7c22f61b3"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xababa8bb0e31d31c0132f098628df71cb9bc00efaee65d2314f8b81a3ba7102c"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xadc1d2b070b1e717c1cc401d9ae254fdedc2b7fea8b76dd707748af41682dfb0"),
        Bytes.fromHexString("0x8b0d50f1b47a8548170febcb"));
    keys.put(
        Bytes.fromHexString("0xb10e2d527612073b26eecdfd717e6a320cf44b4afac2b0732d9fcbe2b7fa0cf6"),
        Bytes.fromHexString("0x947a250d5630b4cf539739df2c5dacb4c659f2488d"));
    keys.put(
        Bytes.fromHexString("0xb121c297e177ec6e8e363868400e6191f38d25028cf69ae280f9ef088aee2aa0"),
        Bytes.fromHexString("0x8b0653f354bad2e6aa3b6617"));
    keys.put(
        Bytes.fromHexString("0xb13d2d76d1f4b7be834882e410b3e3a8afaf69f83600ae24db354391d2378d2e"),
        Bytes.fromHexString("0x94de3b8b6fd542818866484e66aff6e7c050f69e05"));
    keys.put(
        Bytes.fromHexString("0xb174ce5a08510342c376ef6c8bc60d16f5e0c3802d7e75a9736208422c593099"),
        Bytes.fromHexString("0x8b0d0479d6c5f07725c55b79"));
    keys.put(
        Bytes.fromHexString("0xb49d1a566cd6eefb7b0391aa0733a482713096f394e296d4980adc1418e04bea"),
        Bytes.fromHexString("0x8b06c51790d525179f900e79"));
    keys.put(
        Bytes.fromHexString("0xb607e9c59f93ad8a1f602334d86c60f490107853cd582b44a4ae7b8918a7cf3f"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0xb73325b5e5012b9d74b9cdc7381db8adf5dfa789a916815097c0a7be8102975b"),
        Bytes.fromHexString("0x8b06e522e3d50a78a27f41b8"));
    keys.put(
        Bytes.fromHexString("0xba1aa4e35870f18500bdd9eb9d0c635ddb4fd185e987300388e9c669732f18b6"),
        Bytes.fromHexString("0x8b02d72fdcd5847236dc6ab6"));
    keys.put(
        Bytes.fromHexString("0xbb7b4a454dc3493923482f07822329ed19e8244eff582cc204f8554c3620c3fd"),
        Bytes.fromHexString("0x958373409b76b2fd596f2571cfed6fb3a5a3e111af00"));
    keys.put(
        Bytes.fromHexString("0xbcc08685ba06bf1be6799bba25f76ff7a1089974d54237788226de81f3950cb7"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xbce2dfb9b7ca49de25f12886ef09badb1d20f3f37b39a5ea8924d6fd1b82e3c6"),
        Bytes.fromHexString("0x8b0dfde3d9617d977ece2edc"));
    keys.put(
        Bytes.fromHexString("0xbde28a8c022e91e5f7c37f0b649d53ec8b97a8500c74acb4db33307d09b58d96"),
        Bytes.fromHexString(
            "0xa0fffffffffffffffffffffffffffffffffffffffffff973b81e8669646fb18951"));
    keys.put(
        Bytes.fromHexString("0xbeb8c785c347ef286a472a604b30ed9f987d6f34c63b84a8175f0190d1799887"),
        Bytes.fromHexString("0x8b0a794709cef88b17975d8b"));
    keys.put(
        Bytes.fromHexString("0xbef48c14f08c09b5c0a5688fcd62ed3bf9d2e1fd809639144a6bf80de0538930"),
        Bytes.fromHexString("0x8b0909852dd1a3169309bf4d"));
    keys.put(
        Bytes.fromHexString("0xbf3ab95ccf80e3e78ff126f72ae0b21f1f8b34b97b326ab9ef00dc7c09b51549"),
        Bytes.fromHexString("0x8b0688b340df9610b8e38454"));
    keys.put(
        Bytes.fromHexString("0xbf729b5075bcabe68fbc12d6441e499b8e12bc210261e272f0e68939d5563ec2"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xbfd6366cc08d1933f729a5e83a6b00687f32cf1a4040d217520488048d13dd34"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xc252968f53ed5677b655842493a210bca68ce53e0960038e2b27327ab4d6d118"),
        Bytes.fromHexString("0x8b03372b2970dd683b26c28b"));
    keys.put(
        Bytes.fromHexString("0xc350c11b4c234e277eade2ecbfc0a2736875075bd9c452aee2cbf67386c58584"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xc3ed8121ae1e3634b35889746763212c1b9bdc1ef12707bc672778b44b5a01b2"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xc624b66cc0138b8fabc209247f72d758e1cf3343756d543badbf24212bed8c15"),
        Bytes.fromHexString("0x947a250d5630b4cf539739df2c5dacb4c659f2488d"));
    keys.put(
        Bytes.fromHexString("0xc65a7bb8d6351c1cf70c95a316cc6a92839c986682d98bc35f958f4883f9d2a8"),
        Bytes.fromHexString("0x8a152d02c7e14af6800000"));
    keys.put(
        Bytes.fromHexString("0xc6e1e63002a1c0c786a8c46fee8e10edd50384c5b6ca5bd64f0365addf914382"),
        Bytes.fromHexString("0x8b0a8beae28cb4d5c4c00000"));
    keys.put(
        Bytes.fromHexString("0xca1b89e6c526be5a9aaac22a269d60c94da3c4dde7fc06fa36ca53f36d71c592"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0xd2e25cc2a4aaa492e0270e306f5c0f2371674eb05db25793ddd7009c447231b4"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0xd4c6b4a7436b7874406580c8c45ee03ebc5d28e3a461c8a7bb06349cd342a9bc"),
        Bytes.fromHexString("0x8b2009bdc482444c2969237b"));
    keys.put(
        Bytes.fromHexString("0xd7aa9fbc89607c3959595ff5a5442ac98ff739bcff3a0839d9a2bc7206dbb9df"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0xd7b6990105719101dabeb77144f2a3385c8033acd3af97e9423a695e81ad1eb5"),
        Bytes.fromHexString("0x8c033b2e3c9fd0803ce8000000"));
    keys.put(
        Bytes.fromHexString("0xd833147d7dc355ba459fc788f669e58cfaf9dc25ddcd0702e87d69c7b5124289"),
        Bytes.fromHexString("0x946e88c348570fc1e6cd50a05491a3b70baee3e0da"));
    keys.put(
        Bytes.fromHexString("0xdf6966c971051c3d54ec59162606531493a51404a002842f56009d7e5cf4a8c7"),
        Bytes.fromHexString("0x8401010101"));
    keys.put(
        Bytes.fromHexString("0xe13c997314e6b8d754521edd431a547e13dc1807ff0c53bd1ee5230690582dc6"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0xe239b03503a21025830072378d93db18a9a51009f87c1d5b721d4a53fb723b91"),
        Bytes.fromHexString("0x8b0a31eb96bb375730bf9165"));
    keys.put(
        Bytes.fromHexString("0xe320214b85bd84edb23bcc6c80df77e9a0644f1250a1c832a9a064265dd37b84"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0xe3af4c4258fcf619beb348ab76d64d9f7d4ff84760d5538d73b226e684e18dba"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xe55d7c6cba4c4caacc2eee2e4543d9c9f9b958514906ccb0f5f046636abed74a"),
        Bytes.fromHexString(
            "0xa0fffffffffffffffffffffffffffffffffffffffffcc4d1c3602f7fc317ffffff"));
    keys.put(
        Bytes.fromHexString("0xe621d8354b3a97017d5e4b2311d3f4bbe1afb63012217f11d9673076273ffcba"),
        Bytes.fromHexString("0x8b09ac231490efa6ad45f096"));
    keys.put(
        Bytes.fromHexString("0xe8476941d9fe4eede191397d2ab685673f6d35107d739b11c8b25b0badfde726"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xeedf823580aeda0dc636edf907d168910d592cc09c2fbc1b57190d4885133634"),
        Bytes.fromHexString("0x01"));
    keys.put(
        Bytes.fromHexString("0xf009bc507af4d48346adaf842d1a0d7a96a9ddb9fcb835de69107c4f9a6342ea"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xf13e64ff6b24058a5503dde5cd5ce3e3f1763d8b00979da859d5f834664f49b6"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xf1be2163d0abf179a1e4f7c50c12bec6b1b84bb647241460c144dcc42b9f1830"),
        Bytes.fromHexString("0x8b03ee50fdbc6d0856008b3f"));
    keys.put(
        Bytes.fromHexString("0xf2a738958be9819f813f68a70b73ba922f67b55fc597aa77498bbff3ec98c19f"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xf3f7a9fe364faab93b216da50a3214154f22a0a2b415b23a84c8169e8b636ee3"),
        Bytes.fromHexString("0x81c8"));
    keys.put(
        Bytes.fromHexString("0xf4a7e0aede2e61d22319d37d205aeb733cf31d58dfc49ea0a156c8b16d02244d"),
        Bytes.fromHexString("0x8b0383f8f62ee6f1ec400000"));
    keys.put(
        Bytes.fromHexString("0xf54f27c3ea8d007551e18a0bfb349e18c2eba9da15862eb338638206751ed758"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xf652222313e28459528d920b65115c16c04f3efc82aaedc97be59f3f377c0d3f"),
        Bytes.fromHexString("0x8204b0"));
    keys.put(
        Bytes.fromHexString("0xfa2c33602899bbe64537bf8e1f8a57a3069d3ece6a8974d4e1488f8fce28d382"),
        Bytes.fromHexString("0x8b0707f1ec5dcde3d8800000"));
    keys.put(
        Bytes.fromHexString("0xfa917a9d76d6cbc2657c531a5f84946b9cc2b13efb3a33d74f4811afff7d4bdf"),
        Bytes.fromHexString(
            "0xa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    keys.put(
        Bytes.fromHexString("0xfe01bf0e9b8047a1d53af9a3dcba61a03b6afd2f5950e397f9a03e1fc6459ec8"),
        Bytes.fromHexString("0x01"));
    final MerklePatriciaTrie<Bytes, Bytes> trie =
        new SimpleMerklePatriciaTrie<>(Function.identity());
    System.out.println("trie before " + trie.getRootHash());
    keys.forEach((bytes, bytes2) -> trie.put(bytes, bytes2));
    System.out.println(
        "trie after (should be 0x55749318333dd1eb02b74dc52fcb8c1ef22468950cb902ec4c198c8cb15adade)"
            + trie.getRootHash());
  }

  public WorldStateProofProvider(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
  }

  public Optional<WorldStateProof> getAccountProof(
      final Hash worldStateRoot,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys) {

    if (!worldStateStorage.isWorldStateAvailable(worldStateRoot, null)) {
      return Optional.empty();
    } else {
      final Hash accountHash = Hash.hash(accountAddress);
      final Proof<Bytes> accountProof =
          newAccountStateTrie(worldStateRoot).getValueWithProof(accountHash);

      return accountProof
          .getValue()
          .map(RLP::input)
          .map(StateTrieAccountValue::readFrom)
          .map(
              account -> {
                final SortedMap<UInt256, Proof<Bytes>> storageProofs =
                    getStorageProofs(accountHash, account, accountStorageKeys);
                return new WorldStateProof(account, accountProof, storageProofs);
              });
    }
  }

  private SortedMap<UInt256, Proof<Bytes>> getStorageProofs(
      final Hash accountHash,
      final StateTrieAccountValue account,
      final List<UInt256> accountStorageKeys) {
    final MerklePatriciaTrie<Bytes32, Bytes> storageTrie =
        newAccountStorageTrie(accountHash, account.getStorageRoot());
    final NavigableMap<UInt256, Proof<Bytes>> storageProofs = new TreeMap<>();
    accountStorageKeys.forEach(
        key -> storageProofs.put(key, storageTrie.getValueWithProof(Hash.hash(key))));
    return storageProofs;
  }

  public List<Bytes> getAccountProofRelatedNodes(
      final Hash worldStateRoot, final Bytes accountHash) {
    final Proof<Bytes> accountProof =
        newAccountStateTrie(worldStateRoot).getValueWithProof(accountHash);
    return accountProof.getProofRelatedNodes();
  }

  public List<Bytes> getStorageProofRelatedNodes(
      final Hash worldStateRoot, final Hash accountHash, final Hash slotHash) {
    final Proof<Bytes> storageProof =
        newAccountStorageTrie(accountHash, worldStateRoot).getValueWithProof(slotHash);
    return storageProof.getProofRelatedNodes();
  }

  private MerklePatriciaTrie<Bytes, Bytes> newAccountStateTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode, rootHash, b -> b, b -> b);
  }

  private MerklePatriciaTrie<Bytes32, Bytes> newAccountStorageTrie(
      final Hash accountHash, final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        (location, hash) ->
            worldStateStorage.getAccountStorageTrieNode(accountHash, location, hash),
        rootHash,
        b -> b,
        b -> b);
  }

  public boolean isValidRangeProof(
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final Bytes32 rootHash,
      final List<Bytes> proofs,
      final TreeMap<Bytes32, Bytes> keys) {

    // check if it's monotonic increasing
    if (!Ordering.natural().isOrdered(keys.keySet())) {
      return false;
    }

    // when proof is empty we need to have all of the keys to reconstruct the trie
    if (proofs.isEmpty()) {
      final MerklePatriciaTrie<Bytes, Bytes> trie =
          new SimpleMerklePatriciaTrie<>(Function.identity());
      // add the received keys in the trie
      for (Map.Entry<Bytes32, Bytes> key : keys.entrySet()) {
        trie.put(key.getKey(), key.getValue());
      }

      return rootHash.equals(trie.getRootHash());
    }

    // reconstruct a part of the trie with the proof
    Map<Bytes32, Bytes> proofsEntries = Collections.synchronizedMap(new HashMap<>());
    for (Bytes proof : proofs) {
      proofsEntries.put(Hash.hash(proof), proof);
    }

    if (keys.isEmpty()) {
      final MerklePatriciaTrie<Bytes, Bytes> trie =
          new StoredMerklePatriciaTrie<>(
              new InnerNodeDiscoveryManager<>(
                  (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
                  Function.identity(),
                  Function.identity(),
                  startKeyHash,
                  endKeyHash,
                  false),
              rootHash);
      try {
        // check is there is not missing element
        trie.entriesFrom(startKeyHash, Integer.MAX_VALUE);
      } catch (MerkleTrieException e) {
        return false;
      }
      return true;
    }

    // search inner nodes in the range created by the proofs and remove
    final InnerNodeDiscoveryManager<Bytes> snapStoredNodeFactory =
        new InnerNodeDiscoveryManager<>(
            (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
            Function.identity(),
            Function.identity(),
            startKeyHash,
            keys.lastKey(),
            true);
    final MerklePatriciaTrie<Bytes, Bytes> trie =
        new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, rootHash);
    trie.visitAll(node -> {});
    final Bytes[] innerNodes = snapStoredNodeFactory.getInnerNodes().toArray(new Bytes[0]);
    for (Bytes innerNode : innerNodes) {
      trie.removePath(innerNode, new RemoveVisitor<>(false));
    }

    assert !rootHash.equals(trie.getRootHash());

    // add the received keys in the trie to reconstruct the trie
    for (Map.Entry<Bytes32, Bytes> account : keys.entrySet()) {
      trie.put(account.getKey(), account.getValue());
    }

    // check if the generated root hash is valid
    return rootHash.equals(trie.getRootHash());
  }
}
