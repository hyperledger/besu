package org.hyperledger.besu.ethereum.verkletrie;

import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.verkle.StoredVerkleTrie;
import org.hyperledger.besu.ethereum.trie.verkle.factory.StoredNodeFactory;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class VerkleTrie {

  private final org.hyperledger.besu.ethereum.trie.verkle.VerkleTrie<Bytes, Bytes> verkleTrie;

  private final StoredNodeFactory<Bytes> nodeFactory;

  public VerkleTrie(final NodeLoader nodeLoader, final Bytes32 rootHash) {
    nodeFactory = new StoredNodeFactory<>(nodeLoader, value -> value);
    verkleTrie = new StoredVerkleTrie<>(nodeFactory);
  }

  public Optional<Bytes> get(final Bytes key) {
    return verkleTrie.get(key);
  }

  public Optional<Bytes> put(final Bytes key, final Bytes value) {
    return verkleTrie.put(key, Bytes32.leftPad(value));
  }

  public void remove(final Bytes key) {
    verkleTrie.remove(Bytes32.wrap(key));
  }

  public Bytes32 getRootHash() {
    return verkleTrie.getRootHash();
  }

  public void commit(final NodeUpdater nodeUpdater) {
    verkleTrie.commit(nodeUpdater);
  }
}
