package tech.pegasys.pantheon.ethereum.worldstate;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

public class KeyValueStorageWorldStateStorage implements WorldStateStorage {

  private final KeyValueStorage keyValueStorage;

  public KeyValueStorageWorldStateStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public Optional<BytesValue> getCode(final Hash codeHash) {
    return keyValueStorage.get(codeHash);
  }

  @Override
  public Optional<BytesValue> getAccountStateTrieNode(final Bytes32 nodeHash) {
    return keyValueStorage.get(nodeHash);
  }

  @Override
  public Optional<BytesValue> getAccountStorageTrieNode(final Bytes32 nodeHash) {
    return keyValueStorage.get(nodeHash);
  }

  @Override
  public Updater updater() {
    return new Updater(keyValueStorage.getStartTransaction());
  }

  public static class Updater implements WorldStateStorage.Updater {

    private final KeyValueStorage.Transaction transaction;

    public Updater(final KeyValueStorage.Transaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public void putCode(final BytesValue code) {
      transaction.put(Hash.hash(code), code);
    }

    @Override
    public void putAccountStateTrieNode(final Bytes32 nodeHash, final BytesValue node) {
      transaction.put(nodeHash, node);
    }

    @Override
    public void putAccountStorageTrieNode(final Bytes32 nodeHash, final BytesValue node) {
      transaction.put(nodeHash, node);
    }

    @Override
    public void commit() {
      transaction.commit();
    }

    @Override
    public void rollback() {
      transaction.rollback();
    }
  }
}
