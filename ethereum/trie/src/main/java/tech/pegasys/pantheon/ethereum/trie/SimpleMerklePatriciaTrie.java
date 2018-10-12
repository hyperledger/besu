package net.consensys.pantheon.ethereum.trie;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.consensys.pantheon.ethereum.trie.CompactEncoding.bytesToPath;

import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * An in-memory {@link MerklePatriciaTrie}.
 *
 * @param <V> The type of values stored by this trie.
 */
public class SimpleMerklePatriciaTrie<K extends BytesValue, V> implements MerklePatriciaTrie<K, V> {
  private final PathNodeVisitor<V> getVisitor = new GetVisitor<>();
  private final PathNodeVisitor<V> removeVisitor = new RemoveVisitor<>();
  private final DefaultNodeFactory<V> nodeFactory;

  private Node<V> root;

  /**
   * Create a trie.
   *
   * @param valueSerializer A function for serializing values to bytes.
   */
  public SimpleMerklePatriciaTrie(final Function<V, BytesValue> valueSerializer) {
    this.nodeFactory = new DefaultNodeFactory<>(valueSerializer);
    this.root = NullNode.instance();
  }

  @Override
  public Optional<V> get(final K key) {
    checkNotNull(key);
    return root.accept(getVisitor, bytesToPath(key)).getValue();
  }

  @Override
  public void put(final K key, final V value) {
    checkNotNull(key);
    checkNotNull(value);
    this.root = root.accept(new PutVisitor<>(nodeFactory, value), bytesToPath(key));
  }

  @Override
  public void remove(final K key) {
    checkNotNull(key);
    this.root = root.accept(removeVisitor, bytesToPath(key));
  }

  @Override
  public Bytes32 getRootHash() {
    return root.getHash();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + getRootHash() + "]";
  }

  @Override
  public void commit(final NodeUpdater nodeUpdater) {
    // Nothing to do here
  }

  @Override
  public Map<Bytes32, V> entriesFrom(final Bytes32 startKeyHash, final int limit) {
    return StorageEntriesCollector.collectEntries(root, startKeyHash, limit);
  }
}
