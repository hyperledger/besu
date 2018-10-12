package net.consensys.pantheon.ethereum.trie;

/**
 * This exception is thrown when there is an issue retrieving or decoding values from {@link
 * MerkleStorage}.
 */
public class MerkleStorageException extends RuntimeException {

  public MerkleStorageException(final String message) {
    super(message);
  }

  public MerkleStorageException(final String message, final Exception cause) {
    super(message, cause);
  }
}
