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
import org.hyperledger.besu.ethereum.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.bonsai.storage.flat.FullFlatDbStrategy;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.io.IOException;
import java.util.Arrays;
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

public class BonsaiWorldStateTrieLogStorage extends BonsaiWorldStateKeyValueStorage {

  public BonsaiWorldStateTrieLogStorage(
      final Blockchain blockchain,
      final TrieLog trieLog,
      final BonsaiWorldStateKeyValueStorage bonsaiWorldStateKeyValueStorage) {
    super(
        FlatDbMode.FULL,
        new FullFlatDbStrategy(bonsaiWorldStateKeyValueStorage.metricsSystem),
        new TrieLogKeyValueStorage(blockchain, trieLog),
        bonsaiWorldStateKeyValueStorage.trieLogStorage,
        bonsaiWorldStateKeyValueStorage.metricsSystem);
  }

  static class TrieLogKeyValueStorage implements SegmentedKeyValueStorage {

    private final Blockchain blockchain;

    private final TrieLog trieLog;

    private final Map<Hash, Address> addressMapping;

    public TrieLogKeyValueStorage(final Blockchain blockchain, final TrieLog trieLog) {
      this.blockchain = blockchain;
      this.trieLog = trieLog;
      this.addressMapping = new HashMap<>();
      this.trieLog
          .getAccountChanges()
          .forEach(
              (address, accountValueLogTuple) -> addressMapping.put(Hash.hash(address), address));
    }

    @Override
    public Optional<byte[]> get(final SegmentIdentifier segment, final byte[] key)
        throws StorageException {
      if (segment.equals(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE)) {
        final Hash accountHash = Hash.wrap(Bytes32.wrap(key, Hash.SIZE));
        return trieLog
            .getPriorAccount(addressMapping.get(accountHash))
            .map(BonsaiAccount.class::cast)
            .map(BonsaiAccount::serializeAccount)
            .map(Bytes::toArrayUnsafe);
      } else if (segment.equals(KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE)) {
        final Hash accountHash = Hash.wrap(Bytes32.wrap(key, Hash.SIZE));
        final Hash slotHash = Hash.wrap(Bytes32.wrap(key, Hash.SIZE));
        return trieLog
            .getPriorStorageByStorageSlotKey(
                addressMapping.get(accountHash), new StorageSlotKey(slotHash, Optional.empty()))
            .map(Bytes::toArrayUnsafe);
      } else if (segment.equals(KeyValueSegmentIdentifier.CODE_STORAGE)) {
        final Hash accountHash = Hash.wrap(Bytes32.wrap(key, Hash.SIZE));
        return trieLog.getPriorCode(addressMapping.get(accountHash)).map(Bytes::toArrayUnsafe);
      } else if (segment.equals(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE)) {
        if (Arrays.equals(key, BonsaiWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY)) {
          return blockchain
              .getBlockHeader(trieLog.getBlockHash())
              .map(BlockHeader::getStateRoot)
              .map(Bytes::toArrayUnsafe);
        } else if (Arrays.equals(key, BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY)) {
          return Optional.of(trieLog.getBlockHash()).map(Bytes::toArrayUnsafe);
        } else {
          return Optional.empty();
        }
      }
      return Optional.empty();
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
