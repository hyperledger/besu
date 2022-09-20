package org.hyperledger.besu.ethereum.worldstate;

import static org.slf4j.LoggerFactory.getLogger;

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

  public void traverseHardcodedAccounts() {
    traverse(
        Hash.fromHexString("0xab14d68802a763f7db875346d03fbf86f137de55814b191c069e721f47474733"),
        Hash.fromHexString("0xc2aec71cf00dd782c8767c016bfec3eb9cd487eddc065d1fe8f2758eda85699e"),
        Hash.fromHexString("0x7b5855bb92cd7f3f78137497df02f6ccb9badda93d9782e0f230c807ba728be0"));
    //    traverse(
    //            Hash.hash(Address.fromHexString("0xdac17f958d2ee523a2206206994597c13d831ec7")),
    //            Hash.hash(Address.fromHexString("0x57f1887a8bf19b14fc0df6fd9b2acc9af147ea85")),
    //            Hash.hash(Address.fromHexString("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")));
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
    keyValueStorageTransaction.put(
        Bytes.concatenate(
                accountHash, getSlotHash(node.getLocation().orElseThrow(), node.getPath()))
            .toArrayUnsafe(),
        Bytes32.leftPad(org.apache.tuweni.rlp.RLP.decodeValue(node.getValue().orElseThrow()))
            .toArrayUnsafe());
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
