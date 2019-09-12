/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;

import tech.pegasys.pantheon.ethereum.core.Log;
import tech.pegasys.pantheon.ethereum.core.LogSeries;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorageTransaction;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.util.List;
import java.util.Optional;

public class PrivateTransactionKeyValueStorage implements PrivateTransactionStorage {

  private final KeyValueStorage keyValueStorage;

  private static final BytesValue EVENTS_KEY_SUFFIX = BytesValue.of("EVENTS".getBytes(UTF_8));
  private static final BytesValue OUTPUT_KEY_SUFFIX = BytesValue.of("OUTPUT".getBytes(UTF_8));

  public PrivateTransactionKeyValueStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public Optional<List<Log>> getEvents(final Bytes32 transactionHash) {
    return get(transactionHash, EVENTS_KEY_SUFFIX).map(this::rlpDecodeLog);
  }

  @Override
  public Optional<BytesValue> getOutput(final Bytes32 transactionHash) {
    return get(transactionHash, OUTPUT_KEY_SUFFIX);
  }

  @Override
  public boolean isPrivateStateAvailable(final Bytes32 transactionHash) {
    return get(transactionHash, EVENTS_KEY_SUFFIX).isPresent()
        || get(transactionHash, OUTPUT_KEY_SUFFIX).isPresent();
  }

  private List<Log> rlpDecodeLog(final BytesValue bytes) {
    return RLP.input(bytes).readList(Log::readFrom);
  }

  private Optional<BytesValue> get(final BytesValue key, final BytesValue keySuffix) {
    return keyValueStorage
        .get(BytesValues.concatenate(key, keySuffix).getArrayUnsafe())
        .map(BytesValue::wrap);
  }

  @Override
  public Updater updater() {
    return new Updater(keyValueStorage.startTransaction());
  }

  public static class Updater implements PrivateTransactionStorage.Updater {

    private final KeyValueStorageTransaction transaction;

    private Updater(final KeyValueStorageTransaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public PrivateTransactionStorage.Updater putTransactionLogs(
        final Bytes32 transactionHash, final LogSeries logs) {
      set(transactionHash, EVENTS_KEY_SUFFIX, RLP.encode(logs::writeTo));
      return this;
    }

    @Override
    public PrivateTransactionStorage.Updater putTransactionResult(
        final Bytes32 transactionHash, final BytesValue events) {
      set(transactionHash, OUTPUT_KEY_SUFFIX, events);
      return this;
    }

    private void set(final BytesValue key, final BytesValue keySuffix, final BytesValue value) {
      transaction.put(
          BytesValues.concatenate(key, keySuffix).getArrayUnsafe(), value.getArrayUnsafe());
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
