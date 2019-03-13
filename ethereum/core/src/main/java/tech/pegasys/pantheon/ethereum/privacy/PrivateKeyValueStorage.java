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

import tech.pegasys.pantheon.ethereum.core.Log;
import tech.pegasys.pantheon.ethereum.core.LogSeries;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.util.List;
import java.util.Optional;

public class PrivateKeyValueStorage implements PrivateTransactionStorage {

  private final KeyValueStorage keyValueStorage;

  private static final BytesValue LOGS_PREFIX = BytesValue.of(1);
  private static final BytesValue EVENTS_PREFIX = BytesValue.of(2);

  public PrivateKeyValueStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public Optional<List<Log>> getEvents(final Bytes32 transactionHash) {
    return get(LOGS_PREFIX, transactionHash).map(this::rlpDecodeLog);
  }

  @Override
  public Optional<BytesValue> getOutput(final Bytes32 transactionHash) {
    return get(EVENTS_PREFIX, transactionHash);
  }

  @Override
  public boolean isPrivateStateAvailable(final Bytes32 transactionHash) {
    return get(LOGS_PREFIX, transactionHash).isPresent()
        || get(EVENTS_PREFIX, transactionHash).isPresent();
  }

  private List<Log> rlpDecodeLog(final BytesValue bytes) {
    return RLP.input(bytes).readList(Log::readFrom);
  }

  private Optional<BytesValue> get(final BytesValue prefix, final BytesValue key) {
    return keyValueStorage.get(BytesValues.concatenate(prefix, key));
  }

  @Override
  public Updater updater() {
    return new Updater(keyValueStorage.startTransaction());
  }

  public static class Updater implements PrivateTransactionStorage.Updater {

    private final KeyValueStorage.Transaction transaction;

    private Updater(final KeyValueStorage.Transaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public PrivateTransactionStorage.Updater putTransactionLogs(
        final Bytes32 transactionHash, final LogSeries logs) {
      set(LOGS_PREFIX, transactionHash, RLP.encode(logs::writeTo));
      return this;
    }

    @Override
    public PrivateTransactionStorage.Updater putTransactionResult(
        final Bytes32 transactionHash, final BytesValue events) {
      set(EVENTS_PREFIX, transactionHash, events);
      return this;
    }

    private void set(final BytesValue prefix, final BytesValue key, final BytesValue value) {
      transaction.put(BytesValues.concatenate(prefix, key), value);
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
