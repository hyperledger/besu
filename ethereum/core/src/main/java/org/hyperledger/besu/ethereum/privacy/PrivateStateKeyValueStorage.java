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
package org.hyperledger.besu.ethereum.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bouncycastle.util.Arrays;

public class PrivateStateKeyValueStorage implements PrivateStateStorage {

  @Deprecated
  private static final BytesValue EVENTS_KEY_SUFFIX = BytesValue.of("EVENTS".getBytes(UTF_8));

  private static final BytesValue LOGS_KEY_SUFFIX = BytesValue.of("LOGS".getBytes(UTF_8));
  private static final BytesValue OUTPUT_KEY_SUFFIX = BytesValue.of("OUTPUT".getBytes(UTF_8));
  private static final BytesValue METADATA_KEY_SUFFIX = BytesValue.of("METADATA".getBytes(UTF_8));

  private final KeyValueStorage keyValueStorage;

  public PrivateStateKeyValueStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public void performMigrations(
      final PrivacyParameters privacyParameters, final Blockchain blockchain) {
    performStateRootMigration(privacyParameters, blockchain);
    performEventsToLogsMigration();
  }

  @Override
  public Optional<Hash> getLatestStateRoot(final BytesValue privacyId) {
    final byte[] id = privacyId.getArrayUnsafe();

    if (keyValueStorage.get(id).isPresent()) {
      return Optional.of(Hash.wrap(Bytes32.wrap(keyValueStorage.get(id).get())));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Optional<List<Log>> getTransactionLogs(final Bytes32 transactionHash) {
    return get(transactionHash, LOGS_KEY_SUFFIX).map(this::rlpDecodeLog);
  }

  @Override
  public Optional<BytesValue> getTransactionOutput(final Bytes32 transactionHash) {
    return get(transactionHash, OUTPUT_KEY_SUFFIX);
  }

  @Override
  public Optional<PrivateTransactionMetadata> getTransactionMetadata(
      final Bytes32 blockHash, final Bytes32 transactionHash) {
    return get(BytesValues.concatenate(blockHash, transactionHash), METADATA_KEY_SUFFIX)
        .map(
            bytesValue ->
                PrivateTransactionMetadata.readFrom(new BytesValueRLPInput(bytesValue, false)));
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
    public Updater putLatestStateRoot(final BytesValue privacyId, final Hash privateStateHash) {
      transaction.put(privacyId.getArrayUnsafe(), privateStateHash.extractArray());
      return this;
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
    public Updater putTransactionMetadata(
        final Bytes32 blockHash,
        final Bytes32 transactionHash,
        final PrivateTransactionMetadata metadata) {
      set(
          BytesValues.concatenate(blockHash, transactionHash),
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

    private void set(final BytesValue key, final BytesValue keySuffix, final BytesValue value) {
      transaction.put(
          BytesValues.concatenate(key, keySuffix).getArrayUnsafe(), value.getArrayUnsafe());
    }
  }

  private void performStateRootMigration(
      final PrivacyParameters privacyParameters, final Blockchain blockchain) {
    final Collection<BytesValue> migrationKeys =
        keyValueStorage.getAllKeysThat(key -> key.length == 32).stream()
            .map(BytesValue::wrap)
            .collect(Collectors.toList());
    final Enclave enclave = new Enclave(privacyParameters.getEnclaveUri());
    final PrivateStateStorage.Updater updater = updater();
    long current = blockchain.getChainHeadBlockNumber();
    while (current != 0) {
      final Block b = blockchain.getBlockByNumber(current).get();
      b.getBody().getTransactions().stream()
          .filter(
              t ->
                  t.getTo().isPresent()
                      && t.getTo()
                          .get()
                          .equals(
                              Address.privacyPrecompiled(privacyParameters.getPrivacyAddress())))
          .sorted(
              Comparator.comparingInt(
                      (Transaction t) ->
                          blockchain.getTransactionLocation(t.hash()).get().getTransactionIndex())
                  .reversed())
          .forEach(
              t -> {
                final BytesValue privacyGroupId =
                    BytesValues.fromBase64(
                        enclave
                            .receive(
                                new ReceiveRequest(
                                    BytesValues.asBase64String(t.getPayload()),
                                    privacyParameters.getEnclavePublicKey()))
                            .getPrivacyGroupId());
                if (migrationKeys.contains(privacyGroupId)) {
                  updater.putTransactionMetadata(
                      b.getHash(),
                      t.hash(),
                      new PrivateTransactionMetadata(getLatestStateRoot(privacyGroupId).get()));
                }
              });
      --current;
    }
    updater.commit();
  }

  private void performEventsToLogsMigration() {
    final PrivateStateStorage.Updater updater = updater();
    keyValueStorage
        .getAllKeysThat(
            key ->
                key.length > EVENTS_KEY_SUFFIX.getArrayUnsafe().length
                    && Arrays.areEqual(
                        Arrays.copyOfRange(
                            key,
                            key.length - 1 - EVENTS_KEY_SUFFIX.getArrayUnsafe().length,
                            key.length - 1),
                        EVENTS_KEY_SUFFIX.getArrayUnsafe()))
        .stream()
        .map(BytesValue::wrap)
        .collect(Collectors.toList())
        .forEach(
            key -> {
              final Bytes32 transactionHash =
                  Bytes32.wrap(
                      Arrays.copyOfRange(
                          key.getArrayUnsafe(),
                          0,
                          key.size() - 1 - EVENTS_KEY_SUFFIX.getArrayUnsafe().length));
              updater.putTransactionLogs(
                  transactionHash,
                  new LogSeries(
                      get(transactionHash, EVENTS_KEY_SUFFIX).map(this::rlpDecodeLog).get()));
            });
    updater.commit();
  }
}
