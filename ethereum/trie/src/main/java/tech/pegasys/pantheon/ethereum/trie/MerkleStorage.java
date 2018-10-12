package tech.pegasys.pantheon.ethereum.trie;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

/** Storage for use in a {@link StoredMerklePatriciaTrie}. */
public interface MerkleStorage {

  /**
   * Returns an {@code Optional} of the content mapped to the hash if it exists; otherwise empty.
   *
   * @param hash The hash for the content.
   * @return an {@code Optional} of the content mapped to the hash if it exists; otherwise empty
   */
  Optional<BytesValue> get(Bytes32 hash);

  /**
   * Updates the content mapped to the specified hash, creating the mapping if one does not already
   * exist.
   *
   * <p>Note: if the storage implementation already contains content for the given hash, it will
   * replace the existing content.
   *
   * @param hash The hash for the content.
   * @param content The content to store.
   */
  void put(Bytes32 hash, BytesValue content);

  /** Persist accumulated changes to underlying storage. */
  void commit();

  /** Throws away any changes accumulated by this store. */
  void rollback();
}
