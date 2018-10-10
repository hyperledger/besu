package net.consensys.pantheon.ethereum.trie;

import static net.consensys.pantheon.crypto.Hash.keccak256;

import net.consensys.pantheon.ethereum.rlp.RLP;
import net.consensys.pantheon.util.bytes.Bytes32;

import java.util.Map;
import java.util.Optional;

/** An Merkle Patricial Trie. */
public interface MerklePatriciaTrie<K, V> {

  Bytes32 EMPTY_TRIE_ROOT_HASH = keccak256(RLP.NULL);

  /**
   * Returns an {@code Optional} of value mapped to the hash if it exists; otherwise empty.
   *
   * @param key The key for the value.
   * @return an {@code Optional} of value mapped to the hash if it exists; otherwise empty
   */
  Optional<V> get(K key);

  /**
   * Updates the value mapped to the specified key, creating the mapping if one does not already
   * exist.
   *
   * @param key The key that corresponds to the value to be updated.
   * @param value The value to associate the key with.
   */
  void put(K key, V value);

  /**
   * Deletes the value mapped to the specified key, if such a value exists (Optional operation).
   *
   * @param key The key of the value to be deleted.
   */
  void remove(K key);

  /**
   * Returns the KECCAK256 hash of the root node of the trie.
   *
   * @return The KECCAK256 hash of the root node of the trie.
   */
  Bytes32 getRootHash();

  /**
   * Commits any pending changes to the underlying storage.
   *
   * @param nodeUpdater used to store the node values
   */
  void commit(NodeUpdater nodeUpdater);

  /**
   * Retrieve up to {@code limit} storage entries beginning from the first entry with hash equal to
   * or greater than {@code startKeyHash}.
   *
   * @param startKeyHash the first key hash to return.
   * @param limit the maximum number of entries to return.
   * @return the requested storage entries as a map of key hash to value.
   */
  Map<Bytes32, V> entriesFrom(Bytes32 startKeyHash, int limit);
}
