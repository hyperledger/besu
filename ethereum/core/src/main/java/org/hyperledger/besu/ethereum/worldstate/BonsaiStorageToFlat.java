package org.hyperledger.besu.ethereum.worldstate;

import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.TrieNodeDecoder;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;

public class BonsaiStorageToFlat {

  private static final Logger LOG = getLogger(BonsaiStorageToFlat.class);

  private final KeyValueStorage trieBranchStorage;
  private final KeyValueStorage storageStorage;
  private KeyValueStorageTransaction keyValueStorageTransaction;

  public BonsaiStorageToFlat(
      final KeyValueStorage trieBranchStorage, final KeyValueStorage storageStorage) {
    this.trieBranchStorage = trieBranchStorage;
    this.storageStorage = storageStorage;
  }

  public void traverse(final Hash accountHash) {
    final Node<Bytes> storageNodeValue = getStorageNodeValue(accountHash, Bytes.EMPTY);
    keyValueStorageTransaction = storageStorage.startTransaction();
    traverseStartingFrom(accountHash, storageNodeValue);
    keyValueStorageTransaction.commit();
  }

  public void traverse(final Hash... accountHashes) {
    keyValueStorageTransaction = storageStorage.startTransaction();
    for (Hash accountHash : accountHashes) {
      LOG.info("Flattening {}", accountHash);
      final Node<Bytes> storageNodeValue = getStorageNodeValue(accountHash, Bytes.EMPTY);
      traverseStartingFrom(accountHash, storageNodeValue);
    }
    keyValueStorageTransaction.commit();
  }

  public void traverse(final Address... accountAddresses) {
    keyValueStorageTransaction = storageStorage.startTransaction();
    for (Address accountAddress : accountAddresses) {
      final Hash accountHash = Hash.hash(accountAddress);

      LOG.info("Flattening {} - {}", accountAddress.toHexString(), accountHash.toHexString());
      final Node<Bytes> storageNodeValue = getStorageNodeValue(accountHash, Bytes.EMPTY);
      traverseStartingFrom(accountHash, storageNodeValue);
    }
    keyValueStorageTransaction.commit();
  }

  public void traverseHardcodedAccounts() {
    traverse(
        Hash.fromHexString("0xab14d68802a763f7db875346d03fbf86f137de55814b191c069e721f47474733"),
        Hash.fromHexString("0xc2aec71cf00dd782c8767c016bfec3eb9cd487eddc065d1fe8f2758eda85699e"),
        Hash.fromHexString("0x7b5855bb92cd7f3f78137497df02f6ccb9badda93d9782e0f230c807ba728be0"));
    //    traverse(
    //            Address.fromHexString("0xdac17f958d2ee523a2206206994597c13d831ec7")),
    //            Address.fromHexString("0x57f1887a8bf19b14fc0df6fd9b2acc9af147ea85")),
    //            Address.fromHexString("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")));
  }

  public void traverseMostUsed() {
    traverse(
        Address.fromHexString("0x000000000000509081d6fcd3ee63e791ad1db763"),
        Address.fromHexString("0x0000000000007f150bd6f54c40a34d7c3d5e9f56"),
        Address.fromHexString("0x00000000006c3852cbef3e08e8df289169ede581"),
        Address.fromHexString("0x0000000000c2d145a2526bd8c716263bfebe1a72"),
        Address.fromHexString("0x000000000dfde7deaf24138722987c9a6991e2d4"),
        Address.fromHexString("0x00000000ae347930bd1e7b0f35588b92280f9e75"),
        Address.fromHexString("0x00000000c2cf7648c169b25ef1c217864bfa38cc"),
        Address.fromHexString("0x02beed1404c69e62b76af6dbdae41bd98bca2eab"),
        Address.fromHexString("0x03f34be1bf910116595db1b11e9d1b2ca5d59659"),
        Address.fromHexString("0x03f7724180aa6b939894b5ca4314783b0b36b329"),
        Address.fromHexString("0x084b1c3c81545d370f3634392de611caabff8148"),
        Address.fromHexString("0x0d959810433d8121715dafb65a042a3250c5b1e5"),
        Address.fromHexString("0x0da18e368271915c87935f4d83fea00953cfa2b1"),
        Address.fromHexString("0x0eae044f00b0af300500f090ea00027097d03000"),
        Address.fromHexString("0x1111111254fb6c44bac0bed2854e76f90643097d"),
        Address.fromHexString("0x1c29a0522184b95d2d7f86092f675c0a36d93f67"),
        Address.fromHexString("0x1c479675ad559dc151f6ec7ed3fbf8cee79582b6"),
        Address.fromHexString("0x25553828f22bdd19a20e4f12f052903cb474a335"),
        Address.fromHexString("0x271682deb8c4e0901d1a1550ad2e64d568e69909"),
        Address.fromHexString("0x283af0b28c62c092c9727f1ee09c02ca627eb7f5"),
        Address.fromHexString("0x2b2e8cda09bba9660dca5cb6233787738ad68329"),
        Address.fromHexString("0x2b591e99afe9f32eaa6214f7b7629768c40eeb39"),
        Address.fromHexString("0x32176455bea5fd4fd305efbd55a45bf2f8e92380"),
        Address.fromHexString("0x33333333333371718a3c2bb63e5f3b94c9bc13be"),
        Address.fromHexString("0x346b5b8844d2548f6ad55089d8939cffbe3acbaf"),
        Address.fromHexString("0x36c72892fcc72b52fa3b82ed3bb2a467d9079b9a"),
        Address.fromHexString("0x39da41747a83aee658334415666f3ef92dd0d541"),
        Address.fromHexString("0x39e23bf386df825d52d3e63aa80d59562ce21bde"),
        Address.fromHexString("0x3be444fb01bee9fa5673c6c0b986a9aae0fd113d"),
        Address.fromHexString("0x3db3f42d1ed92b180fe7dd8efc0e1d711a4eea82"),
        Address.fromHexString("0x495f947276749ce646f68ac8c248420045cb7b5e"),
        Address.fromHexString("0x4e32004d8b81847a670b4a1778ace4dcf2bba01e"),
        Address.fromHexString("0x4fabb145d64652a948d72533023f6e7a623c7c53"),
        Address.fromHexString("0x53e0e51b5ed9202110d7ecd637a4581db8b9879f"),
        Address.fromHexString("0x57c1e0c2adf6eecdb135bcf9ec5f23b319be2c94"),
        Address.fromHexString("0x5bf5bcc5362f88721167c1068b58c60cad075aac"),
        Address.fromHexString("0x5e1a29c0ffc4bf6a5d725727ef6ad4c506ae57a4"),
        Address.fromHexString("0x5e4e65926ba27467555eb562121fac00d24e9dd2"),
        Address.fromHexString("0x617dee16b86534a5d792a4d7a62fb491b544111e"),
        Address.fromHexString("0x65c2e54a4c75ff6da7b6b32369c1677250075fb2"),
        Address.fromHexString("0x68b3465833fb72a70ecdf485e0e4c7bd8665fc45"),
        Address.fromHexString("0x69cf8871f61fb03f540bc519dd1f1d4682ea0bf6"),
        Address.fromHexString("0x6b175474e89094c44da98b954eedeac495271d0f"),
        Address.fromHexString("0x6f3aca0d7d5d8639e314bd20b79abb3e51c06f47"),
        Address.fromHexString("0x7239f01c663f3c9fd7a44cb2bc8d19b67bdd1336"),
        Address.fromHexString("0x74312363e45dcaba76c59ec49a7aa8a65a67eed3"),
        Address.fromHexString("0x75cda57917e9f73705dc8bcf8a6b2f99adbdc5a5"),
        Address.fromHexString("0x767af52d988d1241a346851a1b39ccd11357376e"),
        Address.fromHexString("0x795baa0b8f58bb4af50311d27c25027cc4ba5f9b"),
        Address.fromHexString("0x7a250d5630b4cf539739df2c5dacb4c659f2488d"),
        Address.fromHexString("0x7bc25283a29a3888cab4555ea86ff1a8c18cc90a"),
        Address.fromHexString("0x7c6b58736d774ae714d2297b5f51d5b9940cdd9a"),
        Address.fromHexString("0x7d2768de32b0b80b7a3454c06bdac94a69ddc7a9"),
        Address.fromHexString("0x7d655c57f71464b6f83811c55d84009cd9f5221c"),
        Address.fromHexString("0x7fc66500c84a76ad7e9c93437bfc5ac33e2ddae9"),
        Address.fromHexString("0x83c8f28c26bf6aaca652df1dbbe0e1b56f8baba2"),
        Address.fromHexString("0x85da3727b0be6096654691d26939f62526ad7ffe"),
        Address.fromHexString("0x881d40237659c251811cec9c364ef91dc08d300c"),
        Address.fromHexString("0x881d4032abe4188e2237efcd27ab435e81fc6bb1"),
        Address.fromHexString("0x8f73a9a3f5e5207b48487886f9c49acb0f0c2500"),
        Address.fromHexString("0x9008d19f58aabd9ed0d60971565aa8510560ab41"),
        Address.fromHexString("0x9507c04b10486547584c37bcbd931b2a4fee9a41"),
        Address.fromHexString("0x95ad61b0a150d79219dcf64e1e6cc01f0b64c4ce"),
        Address.fromHexString("0x968b6210cafb39ddf70d08afdbf092b35542f25c"),
        Address.fromHexString("0x98c3d3183c4b8a650614ad179a1a98be0a8d6b8e"),
        Address.fromHexString("0x9c62e0457b89fdce6ced21f8363439c630466473"),
        Address.fromHexString("0xa09129080ed12cf1b1c7a6e723c63e0820e9d3ae"),
        Address.fromHexString("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"),
        Address.fromHexString("0xa0c68c638235ee32657e8f720a23cec1bfc77c77"),
        Address.fromHexString("0xa24787320ede4cc19d800bf87b41ab9539c4da9d"),
        Address.fromHexString("0xa49a0e5ef83cf89ac8aae182f22e6464b229efc8"),
        Address.fromHexString("0xa57bd00134b2850b2a1c55860c9e9ea100fdd6cf"),
        Address.fromHexString("0xa689a977511bdda434ff0c648c2f0aa55276ec39"),
        Address.fromHexString("0xa69babef1ca67a37ffaf7a485dfff3382056e78c"),
        Address.fromHexString("0xa9d1e08c7793af67e9d92fe308d5697fb81d3e43"),
        Address.fromHexString("0xabea9132b05a70803a4e85094fd0e1800777fbef"),
        Address.fromHexString("0xb39f5f5199042086d6f162ededb761d0f33a2849"),
        Address.fromHexString("0xb4b9dc1c77bdbb135ea907fd5a08094d98883a35"),
        Address.fromHexString("0xb8901acb165ed027e32754e0ffe830802919727f"),
        Address.fromHexString("0xbadc0defafcf6d4239bdf0b66da4d7bd36fcf05a"),
        Address.fromHexString("0xbe5dab4a2e9cd0f27300db4ab94bee3a233aeb19"),
        Address.fromHexString("0xbeefbabeea323f07c59926295205d3b7a17e8638"),
        Address.fromHexString("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
        Address.fromHexString("0xc0e4c63e4eff9eb4af7444df4128645da4ec47d4"),
        Address.fromHexString("0xc26064ac72101b555ff2fcc1629a7a867b69c188"),
        Address.fromHexString("0xc36442b4a4522e871399cd717abdd847ab11fe88"),
        Address.fromHexString("0xc65f7273ddc5d7e380b857e001302859ebdb08a6"),
        Address.fromHexString("0xcda72070e455bb31c7690a170224ce43623d0b6f"),
        Address.fromHexString("0xce0f25934deaaddd174427f1978bcd487a85e9fa"),
        Address.fromHexString("0xd7c09e006a2891880331b0f6224071c1e890a98a"),
        Address.fromHexString("0xd9e1ce17f2641f24ae83637ab66a2cca9c378b9f"),
        Address.fromHexString("0xdac17f958d2ee523a2206206994597c13d831ec7"),
        Address.fromHexString("0xdef171fe48cf0115b1d80b88dc8eab59176fee57"),
        Address.fromHexString("0xdef1c0ded9bec7f1a1670819833240f027b25eff"),
        Address.fromHexString("0xe2ddf03ba8cdafd2bb4884e52f7fb46df4fc7dc1"),
        Address.fromHexString("0xe4000004000bd8006e00720000d27d1fa000d43e"),
        Address.fromHexString("0xe42cad6fc883877a76a26a16ed92444ab177e306"),
        Address.fromHexString("0xe544cf993c7d477c7ef8e91d28aca250d135aa03"),
        Address.fromHexString("0xe592427a0aece92de3edee1f18e0157c05861564"),
        Address.fromHexString("0xe5c57131fbeb8cfc2d0bb220dd027aef2332ba6d"),
        Address.fromHexString("0xe66b31678d6c16e9ebf358268a790b763c133750"),
        Address.fromHexString("0xe6a05f25a051a90d5d144c04f783f6999e48e32d"),
        Address.fromHexString("0xeeac6bfba0ef8b49a3a35e7742cc367e81c1954e"),
        Address.fromHexString("0xeffc18fc3b7eb8e676dac549e0c693ad50d1ce31"),
        Address.fromHexString("0xf4dd946d1406e215a87029db56c69e1bcf3e1773"),
        Address.fromHexString("0xf4efd3d0c43a6a7e6a43baf306536cd6d6643553"),
        Address.fromHexString("0xf5c9f957705bea56a7e806943f98f7777b995826"),
        Address.fromHexString("0xfbddadd80fe7bda00b901fbaf73803f2238ae655"));
  }

  private void traverseStartingFrom(final Hash accountHash, final Node<Bytes> node) {
    if (node == null) {
      LOG.info("Root is null");
      return;
    }
    LOG.info("Starting from root {}", node.getHash());
    traverseStorageTrie(accountHash, node);
  }

  private void traverseStorageTrie(final Bytes32 accountHash, final Node<Bytes> parentNode) {

    if (parentNode == null) {
      return;
    }
    final List<Node<Bytes>> nodes =
        TrieNodeDecoder.decodeNodes(parentNode.getLocation().orElseThrow(), parentNode.getRlp());
    nodes.forEach(
        node -> {
          if (nodeIsHashReferencedDescendant(parentNode, node)) {
            traverseStorageTrie(
                accountHash, getStorageNodeValue(accountHash, node.getLocation().orElseThrow()));
          } else {
            if (node.getValue().isPresent()) {
              copyToFlatDatabase(accountHash, node);
            }
          }
        });
  }

  private void copyToFlatDatabase(final Bytes32 accountHash, final Node<Bytes> node) {
    final byte[] key =
        Bytes.concatenate(
                accountHash, getSlotHash(node.getLocation().orElseThrow(), node.getPath()))
            .toArrayUnsafe();
    final Optional<byte[]> bytes = storageStorage.get(key);
    if (bytes.isEmpty()) {
      keyValueStorageTransaction.put(
          key,
          Bytes32.leftPad(org.apache.tuweni.rlp.RLP.decodeValue(node.getValue().orElseThrow()))
              .toArrayUnsafe());
    }
  }

  private Hash getSlotHash(final Bytes location, final Bytes path) {
    return Hash.wrap(Bytes32.wrap(CompactEncoding.pathToBytes(Bytes.concatenate(location, path))));
  }

  private Node<Bytes> getStorageNodeValue(final Bytes32 accountHash, final Bytes location) {
    final Optional<Bytes> bytes =
        trieBranchStorage
            .get(Bytes.concatenate(accountHash, location).toArrayUnsafe())
            .map(Bytes::wrap);
    if (bytes.isEmpty()) {
      LOG.warn("No value found for hash {} at location {}", accountHash, location);
      return null;
    }
    return TrieNodeDecoder.decode(location, bytes.get());
  }

  private boolean nodeIsHashReferencedDescendant(
      final Node<Bytes> parentNode, final Node<Bytes> node) {
    return !Objects.equals(node.getHash(), parentNode.getHash()) && node.isReferencedByHash();
  }
}
