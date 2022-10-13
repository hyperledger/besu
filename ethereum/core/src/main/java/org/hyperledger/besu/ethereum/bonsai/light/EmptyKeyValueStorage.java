package org.hyperledger.besu.ethereum.bonsai.light;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class EmptyKeyValueStorage implements KeyValueStorage {

  @Override
  public void clear() throws StorageException {}

  @Override
  public boolean containsKey(final byte[] key) throws StorageException {
    return false;
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
    return Optional.empty();
  }

  @Override
  public Stream<byte[]> streamKeys() throws StorageException {
    return Stream.empty();
  }

  @Override
  public boolean tryDelete(final byte[] key) throws StorageException {
    return false;
  }

  @Override
  public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
    return Collections.emptySet();
  }

  @Override
  public KeyValueStorageTransaction startTransaction() throws StorageException {
    return new EmptyKeyValueStorageTransaction();
  }

  @Override
  public void close() throws IOException {}
}
