/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.ethereum.bonsai.storage;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BonsaiWorldStateTrieLogStorage extends BonsaiWorldStateLayerStorage {

  public BonsaiWorldStateTrieLogStorage(
      final TrieLog trieLog, final BonsaiWorldStateKeyValueStorage parent) {
    super(
        new TrieLogKeyValueStorage(parent.composedWorldStateStorage, trieLog),
        parent.trieLogStorage,
        parent,
        parent.metricsSystem);
  }

  public BonsaiWorldStateTrieLogStorage(
      final SnappedKeyValueStorage composedWorldStateStorage,
      final KeyValueStorage trieLogStorage,
      final BonsaiWorldStateKeyValueStorage parentWorldStateStorage,
      final ObservableMetricsSystem metricsSystem) {
    super(composedWorldStateStorage, trieLogStorage, parentWorldStateStorage, metricsSystem);
  }

  @Override
  public BonsaiWorldStateLayerStorage clone() {
    return new BonsaiWorldStateTrieLogStorage(
        ((TrieLogKeyValueStorage) composedWorldStateStorage).clone(),
        trieLogStorage,
        parentWorldStateStorage,
        metricsSystem);
  }

  static class TrieLogKeyValueStorage implements SnappedKeyValueStorage {

    private final SegmentedKeyValueStorage parent;
    private final TrieLog trieLog;

    private final Map<Hash, Address> addressMapping;

    public TrieLogKeyValueStorage(final SegmentedKeyValueStorage parent, final TrieLog trieLog) {
      this.parent = parent;
      this.trieLog = trieLog;
      this.addressMapping = new HashMap<>();
      this.trieLog
          .getAccountChanges()
          .forEach(
              (address, accountValueLogTuple) -> {
                addressMapping.put(Hash.hash(address), address);
              });
      this.trieLog
          .getStorageChanges()
          .forEach(
              (address, accountValueLogTuple) -> {
                addressMapping.put(Hash.hash(address), address);
              });
      this.trieLog
          .getCodeChanges()
          .forEach(
              (address, accountValueLogTuple) -> {
                addressMapping.put(Hash.hash(address), address);
              });
    }

    @Override
    public Optional<byte[]> get(final SegmentIdentifier segment, final byte[] key)
        throws StorageException {
      if (segment.equals(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE)) {
        final Address address = addressMapping.get(Hash.wrap(Bytes32.wrap(key)));
        return Optional.of(trieLog)
            .filter(trieLog -> address != null)
            .flatMap(trieLog -> trieLog.getPriorAccount(address))
            .map(StateTrieAccountValue.class::cast)
            .map(account -> RLP.encode(account::writeTo))
            .map(Bytes::toArrayUnsafe)
            .or(() -> getFromParent(segment, key));
      } else if (segment.equals(KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE)) {
        final Address address = addressMapping.get(Hash.wrap(Bytes32.wrap(key, 0)));
        final Hash slotHash = Hash.wrap(Bytes32.wrap(key, Hash.SIZE));
        return Optional.of(trieLog)
            .filter(trieLog -> address != null)
            .flatMap(
                trieLog ->
                    trieLog.getPriorStorageByStorageSlotKey(
                        address, new StorageSlotKey(slotHash, Optional.empty())))
            .map(Bytes::toArrayUnsafe);
      } else if (segment.equals(KeyValueSegmentIdentifier.CODE_STORAGE)) {
        final Address address = addressMapping.get(Hash.wrap(Bytes32.wrap(key, 0)));
        return Optional.of(trieLog)
            .filter(trieLog -> address != null)
            .flatMap(trieLog -> trieLog.getPriorCode(address))
            .map(Bytes::toArrayUnsafe)
            .or(() -> getFromParent(segment, key));
      }
      return getFromParent(segment, key);
    }

    private Optional<byte[]> getFromParent(final SegmentIdentifier segment, final byte[] key)
        throws StorageException {
      return parent.get(segment, key);
    }

    @Override
    public SnappedKeyValueStorage clone() {
      return new TrieLogKeyValueStorage(parent, trieLog);
    }

    @Override
    public Optional<NearestKeyValue> getNearestTo(
        final SegmentIdentifier segmentIdentifier, final Bytes key) throws StorageException {
      throw new NotImplementedException("getNearestTo not available for trieLog");
    }

    @Override
    public SegmentedKeyValueStorageTransaction startTransaction() throws StorageException {
      throw new NotImplementedException("startTransaction not available for trieLog");
    }

    @Override
    public Stream<Pair<byte[], byte[]>> stream(final SegmentIdentifier segmentIdentifier) {
      throw new NotImplementedException("stream not available for trieLog");
    }

    @Override
    public Stream<Pair<byte[], byte[]>> streamFromKey(
        final SegmentIdentifier segmentIdentifier, final byte[] startKey) {
      throw new NotImplementedException("streamFromKey not available for trieLog");
    }

    @Override
    public Stream<Pair<byte[], byte[]>> streamFromKey(
        final SegmentIdentifier segmentIdentifier, final byte[] startKey, final byte[] endKey) {
      throw new NotImplementedException("streamFromKey not available for trieLog");
    }

    @Override
    public Stream<byte[]> streamKeys(final SegmentIdentifier segmentIdentifier) {
      throw new NotImplementedException("streamKeys not available for trieLog");
    }

    @Override
    public boolean tryDelete(final SegmentIdentifier segmentIdentifier, final byte[] key)
        throws StorageException {
      throw new NotImplementedException("tryDelete not available for trieLog");
    }

    @Override
    public Set<byte[]> getAllKeysThat(
        final SegmentIdentifier segmentIdentifier, final Predicate<byte[]> returnCondition) {
      throw new NotImplementedException("getAllKeysThat not available for trieLog");
    }

    @Override
    public Set<byte[]> getAllValuesFromKeysThat(
        final SegmentIdentifier segmentIdentifier, final Predicate<byte[]> returnCondition) {
      throw new NotImplementedException("getAllValuesFromKeysThat not available for trieLog");
    }

    @Override
    public SegmentedKeyValueStorageTransaction getSnapshotTransaction() {
      throw new NotImplementedException("getSnapshotTransaction not available for trieLog");
    }

    @Override
    public void clear(final SegmentIdentifier segmentIdentifier) {
      // noop
    }

    @Override
    public boolean isClosed() {
      return false;
    }

    @Override
    public void close() throws IOException {
      // noop
    }
  }
}
