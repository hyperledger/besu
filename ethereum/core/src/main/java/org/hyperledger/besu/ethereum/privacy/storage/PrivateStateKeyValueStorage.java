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

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionSimulator;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionSimulatorResult;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivateStateKeyValueStorage implements PrivateStateStorage {
  private static final Logger LOG = LogManager.getLogger();

  @Deprecated
  private static final BytesValue EVENTS_KEY_SUFFIX = BytesValue.of("EVENTS".getBytes(UTF_8));

  private static final BytesValue LOGS_KEY_SUFFIX = BytesValue.of("LOGS".getBytes(UTF_8));
  private static final BytesValue OUTPUT_KEY_SUFFIX = BytesValue.of("OUTPUT".getBytes(UTF_8));
  private static final BytesValue METADATA_KEY_SUFFIX = BytesValue.of("METADATA".getBytes(UTF_8));
  private static final BytesValue STATUS_KEY_SUFFIX = BytesValue.of("STATUS".getBytes(UTF_8));
  private static final BytesValue REVERT_KEY_SUFFIX = BytesValue.of("REVERT".getBytes(UTF_8));

  private final KeyValueStorage keyValueStorage;

  public PrivateStateKeyValueStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  public void performMigrations(
      final Enclave enclave,
      final BytesValue enclaveKey,
      final Address privacyAddress,
      final Blockchain blockchain,
      final PrivateTransactionSimulator privateTransactionSimulator) {
    performStateRootMigration(
        enclave, enclaveKey, privacyAddress, blockchain, privateTransactionSimulator);
    performEventsToLogsMigration();
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
  public boolean isPrivateStateAvailable(final Bytes32 transactionHash) {
    return false;
  }

  @Override
  public boolean isWorldStateAvailable(final Bytes32 rootHash) {
    return false;
  }

  @Override
  public Updater updater() {
    return new Updater(keyValueStorage.startTransaction());
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

  private void performStateRootMigration(
      final Enclave enclave,
      final BytesValue enclaveKey,
      final Address privacyAddress,
      final Blockchain blockchain,
      final PrivateTransactionSimulator privateTransactionSimulator) {

    final Set<byte[]> migratedKeys =
        keyValueStorage.getAllKeysThat(containsSuffix(METADATA_KEY_SUFFIX));

    final Set<BytesValue> migrationKeys =
        keyValueStorage.getAllKeysThat(key -> key.length == 32).stream()
            .map(BytesValue::wrap)
            .collect(Collectors.toSet());

    if (migratedKeys.size() != 0 && migrationKeys.size() != 0) {
      LOG.fatal("Privacy database integrity failure");
      throw new RuntimeException();
    }

    if (migrationKeys.size() == 0) {
      LOG.info("Privacy database nothing to migrate");
      return;
    }

    final long headBlockNumber = blockchain.getChainHeadBlockNumber();
    long current = 0;
    while (current <= headBlockNumber) {
      final Block b = blockchain.getBlockByNumber(current).get();
      b.getBody().getTransactions().stream()
          .filter(t -> t.getTo().isPresent() && t.getTo().get().equals(privacyAddress))
          .forEach(
              t -> {
                final ReceiveResponse rr =
                    enclave.receive(
                        new ReceiveRequest(
                            BytesValues.asBase64String(t.getPayload()),
                            BytesValues.asBase64String(enclaveKey)));
                final BytesValue privacyGroupId = BytesValues.fromBase64(rr.getPrivacyGroupId());
                final PrivateTransaction privateTransaction =
                    PrivateTransaction.readFrom(RLP.input(BytesValues.fromBase64(rr.getPayload())));
                final Updater updater = updater();
                if (migrationKeys.contains(privacyGroupId)) {
                  final PrivateTransactionSimulatorResult ptsr =
                      privateTransactionSimulator
                          .process(privateTransaction, privacyGroupId, b.getHeader())
                          .orElseThrow();

                  final PrivateBlockMetadata privateBlockMetadata =
                      getPrivateBlockMetadata(b.getHeader().getHash(), Bytes32.wrap(privacyGroupId))
                          .orElseGet(PrivateBlockMetadata::empty);
                  privateBlockMetadata.addPrivateTransactionMetadata(
                      new PrivateTransactionMetadata(t.getHash(), ptsr.getResultingRootHash()));
                  updater.putPrivateBlockMetadata(
                      Bytes32.wrap(b.getHeader().getHash()),
                      Bytes32.wrap(privacyGroupId),
                      privateBlockMetadata);
                }
                // needs to be committed in the loop because there could be a tx in the same block
                // that depends on the result of this transaction
                updater.commit();
              });
      current++;
    }
    // clean up old keys
    final Updater updater = updater();
    migrationKeys.forEach(key -> updater.remove(key, BytesValue.EMPTY));
    updater.commit();
  }

  private void performEventsToLogsMigration() {
    final Updater updater = updater();
    keyValueStorage.getAllKeysThat(containsSuffix(EVENTS_KEY_SUFFIX)).stream()
        .map(BytesValue::wrap)
        .collect(Collectors.toList())
        .forEach(
            key -> {
              final Bytes32 transactionHash =
                  Bytes32.wrap(
                      Arrays.copyOfRange(
                          key.getArrayUnsafe(),
                          0,
                          key.size() - EVENTS_KEY_SUFFIX.getArrayUnsafe().length));
              updater.putTransactionLogs(
                  transactionHash,
                  new LogSeries(
                      get(transactionHash, EVENTS_KEY_SUFFIX).map(this::rlpDecodeLog).get()));
              updater.remove(transactionHash, EVENTS_KEY_SUFFIX);
            });
    updater.commit();
  }

  private Predicate<byte[]> containsSuffix(final BytesValue suffix) {
    return key ->
        key.length > suffix.getArrayUnsafe().length
            && Arrays.equals(
                Arrays.copyOfRange(key, key.length - suffix.getArrayUnsafe().length, key.length),
                suffix.getArrayUnsafe());
  }

  @Deprecated
  @VisibleForTesting
  Optional<Hash> getLatestStateRoot(final BytesValue privacyGroupId) {
    final byte[] id = privacyGroupId.getArrayUnsafe();
    if (keyValueStorage.get(id).isPresent()) {
      return Optional.of(Hash.wrap(Bytes32.wrap(keyValueStorage.get(id).get())));
    } else {
      return Optional.empty();
    }
  }

  @Deprecated
  @VisibleForTesting
  Optional<List<Log>> getTransactionLogsLegacy(final Bytes32 transactionHash) {
    return get(transactionHash, EVENTS_KEY_SUFFIX).map(this::rlpDecodeLog);
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

    private void remove(final BytesValue key, final BytesValue keySuffix) {
      transaction.remove(BytesValues.concatenate(key, keySuffix).getArrayUnsafe());
    }

    @Deprecated
    @VisibleForTesting
    Updater putLatestStateRoot(final BytesValue privacyId, final Hash privateStateHash) {
      transaction.put(privacyId.getArrayUnsafe(), privateStateHash.extractArray());
      return this;
    }

    @Deprecated
    @VisibleForTesting
    Updater putTransactionLogsLegacy(final Bytes32 transactionHash, final LogSeries logs) {
      set(transactionHash, EVENTS_KEY_SUFFIX, RLP.encode(logs::writeTo));
      return this;
    }
  }
}
