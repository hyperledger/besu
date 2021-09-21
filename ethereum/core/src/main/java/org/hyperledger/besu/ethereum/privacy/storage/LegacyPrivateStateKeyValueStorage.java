/*
 * Copyright ConsenSys AG.
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
 */
package org.hyperledger.besu.ethereum.privacy.storage;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

@Deprecated
public class LegacyPrivateStateKeyValueStorage implements LegacyPrivateStateStorage {

  public static final Bytes EVENTS_KEY_SUFFIX = Bytes.of("EVENTS".getBytes(UTF_8));
  public static final Bytes LOGS_KEY_SUFFIX = Bytes.of("LOGS".getBytes(UTF_8));
  public static final Bytes OUTPUT_KEY_SUFFIX = Bytes.of("OUTPUT".getBytes(UTF_8));
  public static final Bytes METADATA_KEY_SUFFIX = Bytes.of("METADATA".getBytes(UTF_8));
  public static final Bytes STATUS_KEY_SUFFIX = Bytes.of("STATUS".getBytes(UTF_8));
  public static final Bytes REVERT_KEY_SUFFIX = Bytes.of("REVERT".getBytes(UTF_8));

  private final KeyValueStorage keyValueStorage;

  public LegacyPrivateStateKeyValueStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public Optional<Hash> getLatestStateRoot(final Bytes privacyId) {
    final byte[] id = privacyId.toArrayUnsafe();

    if (keyValueStorage.get(id).isPresent()) {
      return Optional.of(Hash.wrap(Bytes32.wrap(keyValueStorage.get(id).get())));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Optional<List<Log>> getTransactionLogs(final Bytes32 transactionHash) {
    final Optional<List<Log>> logs = get(transactionHash, LOGS_KEY_SUFFIX).map(this::rlpDecodeLog);
    if (logs.isEmpty()) {
      return get(transactionHash, EVENTS_KEY_SUFFIX).map(this::rlpDecodeLog);
    }
    return logs;
  }

  @Override
  public Optional<Bytes> getTransactionOutput(final Bytes32 transactionHash) {
    return get(transactionHash, OUTPUT_KEY_SUFFIX);
  }

  @Override
  public Optional<Bytes> getStatus(final Bytes32 transactionHash) {
    return get(transactionHash, STATUS_KEY_SUFFIX);
  }

  @Override
  public Optional<Bytes> getRevertReason(final Bytes32 transactionHash) {
    return get(transactionHash, REVERT_KEY_SUFFIX);
  }

  @Override
  public Optional<PrivateTransactionMetadata> getTransactionMetadata(
      final Bytes32 blockHash, final Bytes32 transactionHash) {
    return get(Bytes.concatenate(blockHash, transactionHash), METADATA_KEY_SUFFIX)
        .map(bytes -> PrivateTransactionMetadata.readFrom(new BytesValueRLPInput(bytes, false)));
  }

  @Override
  public boolean isPrivateStateAvailable(final Bytes32 transactionHash) {
    return false;
  }

  @Override
  public boolean isWorldStateAvailable(final Bytes32 rootHash) {
    return false;
  }

  private Optional<Bytes> get(final Bytes key, final Bytes keySuffix) {
    return keyValueStorage.get(Bytes.concatenate(key, keySuffix).toArrayUnsafe()).map(Bytes::wrap);
  }

  private List<Log> rlpDecodeLog(final Bytes bytes) {
    return RLP.input(bytes).readList(Log::readFrom);
  }

  @Override
  public LegacyPrivateStateStorage.Updater updater() {
    return new LegacyPrivateStateKeyValueStorage.Updater(keyValueStorage.startTransaction());
  }

  public static class Updater implements LegacyPrivateStateStorage.Updater {

    private final KeyValueStorageTransaction transaction;

    private Updater(final KeyValueStorageTransaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public Updater putLatestStateRoot(final Bytes privacyId, final Hash privateStateHash) {
      transaction.put(privacyId.toArrayUnsafe(), privateStateHash.toArray());
      return this;
    }

    @Override
    public Updater putTransactionLogs(final Bytes32 transactionHash, final List<Log> logs) {
      set(transactionHash, LOGS_KEY_SUFFIX, RLP.encode(out -> out.writeList(logs, Log::writeTo)));
      return this;
    }

    @Override
    public Updater putTransactionResult(final Bytes32 transactionHash, final Bytes events) {
      set(transactionHash, OUTPUT_KEY_SUFFIX, events);
      return this;
    }

    @Override
    public Updater putTransactionStatus(final Bytes32 transactionHash, final Bytes status) {
      set(transactionHash, STATUS_KEY_SUFFIX, status);
      return this;
    }

    @Override
    public Updater putTransactionRevertReason(
        final Bytes32 transactionHash, final Bytes revertReason) {
      set(transactionHash, REVERT_KEY_SUFFIX, revertReason);
      return this;
    }

    @Override
    public Updater putTransactionMetadata(
        final Bytes32 blockHash,
        final Bytes32 transactionHash,
        final PrivateTransactionMetadata metadata) {
      set(
          Bytes.concatenate(blockHash, transactionHash),
          METADATA_KEY_SUFFIX,
          RLP.encode(metadata::writeTo));
      return this;
    }

    @Override
    public void commit() {
      transaction.commit();
    }

    @Override
    public void rollback() {
      transaction.rollback();
    }

    private void set(final Bytes key, final Bytes keySuffix, final Bytes value) {
      transaction.put(Bytes.concatenate(key, keySuffix).toArrayUnsafe(), value.toArrayUnsafe());
    }
  }
}
