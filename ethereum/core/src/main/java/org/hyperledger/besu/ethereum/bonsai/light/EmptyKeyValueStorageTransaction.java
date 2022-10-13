package org.hyperledger.besu.ethereum.bonsai.light;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

public class EmptyKeyValueStorageTransaction implements KeyValueStorageTransaction {

  @Override
  public void put(final byte[] key, final byte[] value) {}

  @Override
  public void remove(final byte[] key) {}

  @Override
  public void commit() throws StorageException {}

  @Override
  public void rollback() {}
}
