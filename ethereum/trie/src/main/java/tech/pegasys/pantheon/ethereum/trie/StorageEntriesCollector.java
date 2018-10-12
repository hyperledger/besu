package tech.pegasys.pantheon.ethereum.trie;

import tech.pegasys.pantheon.ethereum.trie.TrieIterator.State;
import tech.pegasys.pantheon.util.bytes.Bytes32;

import java.util.Map;
import java.util.TreeMap;

public class StorageEntriesCollector<V> implements TrieIterator.LeafHandler<V> {

  private final Bytes32 startKeyHash;
  private final int limit;
  private final Map<Bytes32, V> values = new TreeMap<>();

  public StorageEntriesCollector(final Bytes32 startKeyHash, final int limit) {
    this.startKeyHash = startKeyHash;
    this.limit = limit;
  }

  public static <V> Map<Bytes32, V> collectEntries(
      final Node<V> root, final Bytes32 startKeyHash, final int limit) {
    final StorageEntriesCollector<V> entriesCollector =
        new StorageEntriesCollector<>(startKeyHash, limit);
    final TrieIterator<V> visitor = new TrieIterator<>(entriesCollector);
    root.accept(visitor, CompactEncoding.bytesToPath(startKeyHash));
    return entriesCollector.getValues();
  }

  private boolean limitReached() {
    return limit <= values.size();
  }

  @Override
  public State onLeaf(final Bytes32 keyHash, final Node<V> node) {
    if (keyHash.compareTo(startKeyHash) >= 0) {
      node.getValue().ifPresent(value -> values.put(keyHash, value));
    }
    return limitReached() ? State.STOP : State.CONTINUE;
  }

  public Map<Bytes32, V> getValues() {
    return values;
  }
}
