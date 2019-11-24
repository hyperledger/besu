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

import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.List;
import java.util.Optional;

public class PrivateStateKeyValueStorage implements PrivateStateStorage {

  @Deprecated
  private static final BytesValue EVENTS_KEY_SUFFIX = BytesValue.of("EVENTS".getBytes(UTF_8));

  private static final BytesValue LOGS_KEY_SUFFIX = BytesValue.of("LOGS".getBytes(UTF_8));
  private static final BytesValue OUTPUT_KEY_SUFFIX = BytesValue.of("OUTPUT".getBytes(UTF_8));
  private static final BytesValue METADATA_KEY_SUFFIX = BytesValue.of("METADATA".getBytes(UTF_8));
  private static final BytesValue STATUS_KEY_SUFFIX = BytesValue.of("STATUS".getBytes(UTF_8));
  private static final BytesValue REVERT_KEY_SUFFIX = BytesValue.of("REVERT".getBytes(UTF_8));
  private static final BytesValue PRIVACY_GROUP_HEAD_KEY_SUFFIX =
      BytesValue.of("HEAD".getBytes(UTF_8));

  private final KeyValueStorage keyValueStorage;

  public PrivateStateKeyValueStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
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
  public Optional<BytesValue> getTransactionOutput(final Bytes32 transactionHash) {
    return get(transactionHash, OUTPUT_KEY_SUFFIX);
  }

  @Override
  public Optional<BytesValue> getStatus(final Bytes32 transactionHash) {
    return get(transactionHash, STATUS_KEY_SUFFIX);
  }

  @Override
  public Optional<BytesValue> getRevertReason(final Bytes32 transactionHash) {
    return get(transactionHash, REVERT_KEY_SUFFIX);
  }

  @Override
  public Optional<PrivateBlockMetadata> getPrivateBlockMetadata(
      final Bytes32 blockHash, final Bytes32 privacyGroupId) {
    return get(BytesValues.concatenate(blockHash, privacyGroupId), METADATA_KEY_SUFFIX)
        .map(this::rlpDecodePrivateBlockMetadata);
  }

  @Override
  public Optional<BytesValue> getPrivacyGroupHead(
      final Bytes32 blockHash, final Bytes32 privacyGroupId) {
    return get(BytesValues.concatenate(blockHash, privacyGroupId), PRIVACY_GROUP_HEAD_KEY_SUFFIX);
  }

  @Override
  public boolean isPrivateStateAvailable(final Bytes32 transactionHash) {
    return false;
  }

  @Override
  public boolean isWorldStateAvailable(final Bytes32 rootHash) {
    return false;
  }

  private Optional<BytesValue> get(final BytesValue key, final BytesValue keySuffix) {
    return keyValueStorage
        .get(BytesValues.concatenate(key, keySuffix).getArrayUnsafe())
        .map(BytesValue::wrap);
  }

  private List<Log> rlpDecodeLog(final BytesValue bytes) {
    return RLP.input(bytes).readList(Log::readFrom);
  }

  private PrivateBlockMetadata rlpDecodePrivateBlockMetadata(final BytesValue bytes) {
    return PrivateBlockMetadata.readFrom(RLP.input(bytes));
  }

  @Override
  public PrivateStateStorage.Updater updater() {
    return new PrivateStateKeyValueStorage.Updater(keyValueStorage.startTransaction());
  }

  public static class Updater implements PrivateStateStorage.Updater {

    private final KeyValueStorageTransaction transaction;

    private Updater(final KeyValueStorageTransaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public Updater putTransactionLogs(final Bytes32 transactionHash, final LogSeries logs) {
      set(transactionHash, LOGS_KEY_SUFFIX, RLP.encode(logs::writeTo));
      return this;
    }

    @Override
    public Updater putTransactionResult(final Bytes32 transactionHash, final BytesValue events) {
      set(transactionHash, OUTPUT_KEY_SUFFIX, events);
      return this;
    }

    @Override
    public PrivateStateStorage.Updater putTransactionStatus(
        final Bytes32 transactionHash, final BytesValue status) {
      set(transactionHash, STATUS_KEY_SUFFIX, status);
      return this;
    }

    @Override
    public PrivateStateStorage.Updater putTransactionRevertReason(
        final Bytes32 transactionHash, final BytesValue revertReason) {
      set(transactionHash, REVERT_KEY_SUFFIX, revertReason);
      return this;
    }

    @Override
    public Updater putPrivateBlockMetadata(
        final Bytes32 blockHash,
        final Bytes32 privacyGroupId,
        final PrivateBlockMetadata metadata) {
      set(
          BytesValues.concatenate(blockHash, privacyGroupId),
          METADATA_KEY_SUFFIX,
          RLP.encode(metadata::writeTo));
      return this;
    }

    @Override
    public Updater putPrivacyGroupHead(
        final Bytes32 blockHash, final Bytes32 privacyGroupId, final Bytes32 latestBlockHash) {
      set(
          BytesValues.concatenate(blockHash, privacyGroupId),
          PRIVACY_GROUP_HEAD_KEY_SUFFIX,
          latestBlockHash);
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

    private void set(final BytesValue key, final BytesValue keySuffix, final BytesValue value) {
      transaction.put(
          BytesValues.concatenate(key, keySuffix).getArrayUnsafe(), value.getArrayUnsafe());
    }
  }
}
