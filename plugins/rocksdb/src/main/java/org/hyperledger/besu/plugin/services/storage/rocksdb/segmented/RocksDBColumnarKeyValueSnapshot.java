package org.hyperledger.besu.plugin.services.storage.rocksdb.segmented;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbSegmentIdentifier;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class RocksDbColumnarKeyValueSnapshot implements SegmentedKeyValueStorage<RocksDbSegmentIdentifier> {
  @Override
  public RocksDbSegmentIdentifier getSegmentIdentifierByName(SegmentIdentifier segment) {
    return null;
  }

  @Override
  public Optional<byte[]> get(RocksDbSegmentIdentifier segment, byte[] key) throws StorageException {
    return Optional.empty();
  }

  @Override
  public Transaction<RocksDbSegmentIdentifier> startTransaction() throws StorageException {
    return null;
  }

  @Override
  public Stream<byte[]> streamKeys(RocksDbSegmentIdentifier segmentHandle) {
    return null;
  }

  @Override
  public boolean tryDelete(RocksDbSegmentIdentifier segmentHandle, byte[] key) throws StorageException {
    return false;
  }

  @Override
  public Set<byte[]> getAllKeysThat(RocksDbSegmentIdentifier segmentHandle, Predicate<byte[]> returnCondition) {
    return null;
  }

  @Override
  public void clear(RocksDbSegmentIdentifier segmentHandle) {

  }

  @Override
  public void close() throws IOException {

  }
}
