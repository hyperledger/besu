package org.hyperledger.besu.ethereum.trie.diffbased.common;

public interface StorageSubscriber {
  default void onClearStorage() {}

  default void onClearFlatDatabaseStorage() {}

  default void onClearTrieLog() {}

  default void onCloseStorage() {}
}
