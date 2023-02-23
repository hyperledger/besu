package org.hyperledger.besu.ethereum.trie.zkevm;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.ethereum.trie.CommitVisitor;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;
import org.hyperledger.besu.ethereum.trie.Proof;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.trie.patricia.RemoveVisitor;
import org.hyperledger.besu.ethereum.trie.sparse.StoredSparseMerkleTrie;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class ZKTrie implements MerkleTrie<Bytes, Bytes> {

  private static final Bytes NEXT_FREE_NODE_PATH = Bytes.of(0, 0);

  private static final Bytes SUB_TRIE_ROOT_PATH = Bytes.of(0, 1);

  private final StoredSparseMerkleTrie<Bytes, Bytes> state;

  private Bytes nextFreeNode;

  public ZKTrie(final NodeLoader nodeLoader) {
    this.state = new StoredSparseMerkleTrie<>(nodeLoader, b -> b, b -> b);
  }

  @SuppressWarnings("unused")
  private Bytes getNextFreeNode() {
    if (nextFreeNode == null) {
      nextFreeNode = state.get(NEXT_FREE_NODE_PATH).orElse(UInt256.valueOf(0));
    }
    return nextFreeNode;
  }

  private Bytes getNextFreeNodePath() {
    return Bytes.fromHexString(
        Long.toBinaryString(nextFreeNode.toLong())); // TODO implement something clean for that
  }

  @Override
  public Bytes32 getRootHash() {
    return state
        .getPath(SUB_TRIE_ROOT_PATH)
        .map(Hash::keccak256)
        .orElse(EMPTY_TRIE_NODE_HASH); // todo use mimc
  }

  public Bytes32 getZKRoot() {
    return state.getRootHash();
  }

  @Override
  public Optional<Bytes> get(final Bytes key) {
    // flat database -> leaf

    return Optional.empty();
  }

  @Override
  public Optional<Bytes> getPath(final Bytes path) {
    return Optional.empty();
  }

  @Override
  public Proof<Bytes> getValueWithProof(final Bytes key) {

    return null;
  }

  @Override
  public void put(final Bytes key, final Bytes value) {
    state.putPath(Bytes.concatenate(SUB_TRIE_ROOT_PATH, getNextFreeNodePath()), value);
  }

  @Override
  public void putPath(final Bytes key, final Bytes value) {}

  @Override
  public void put(final Bytes key, final PathNodeVisitor<Bytes> putVisitor) {}

  @Override
  public void remove(final Bytes key) {}

  @Override
  public void removePath(final Bytes path, final RemoveVisitor<Bytes> removeVisitor) {}

  @Override
  public void commit(final NodeUpdater nodeUpdater) {}

  @Override
  public void commit(final NodeUpdater nodeUpdater, final CommitVisitor<Bytes> commitVisitor) {}

  @Override
  public Map<Bytes32, Bytes> entriesFrom(final Bytes32 startKeyHash, final int limit) {
    return null;
  }

  @Override
  public Map<Bytes32, Bytes> entriesFrom(final Function<Node<Bytes>, Map<Bytes32, Bytes>> handler) {
    return null;
  }

  @Override
  public void visitAll(final Consumer<Node<Bytes>> nodeConsumer) {}

  @Override
  public CompletableFuture<Void> visitAll(
      final Consumer<Node<Bytes>> nodeConsumer, final ExecutorService executorService) {
    return null;
  }

  @Override
  public void visitLeafs(final TrieIterator.LeafHandler<Bytes> handler) {}
}
