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
package org.hyperledger.besu.ethereum.privacy.storage.migration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateTransactionDataFixture.privacyMarkerTransaction;
import static org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateTransactionDataFixture.privateTransaction;
import static org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateTransactionDataFixture.successfulPrivateTxProcessingResult;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateStorageMigrationTransactionProcessorResult;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor.Result;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateKeyValueStorage;
import org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateStorage.Updater;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivateStorageMigrationTest {

  private static final String PRIVACY_GROUP_ID = "tJw12cPM6EZRF5zfHv2zLePL0cqlaDjLn0x1T/V0yzE=";
  private static final String TRANSACTION_KEY = "93Ky7lXwFkMc7+ckoFgUMku5bpr9tz4zhmWmk9RlNng=";
  private static final Bytes ENCLAVE_KEY = Bytes.EMPTY;
  private static final Address PRIVACY_ADDRESS = Address.DEFAULT_PRIVACY;

  @Mock private Blockchain blockchain;
  @Mock private Enclave enclave;
  @Mock private PrivateStorageMigrationTransactionProcessor migrationTransactionProcessor;

  private LegacyPrivateStateStorage legacyPrivateStateStorage;
  private PrivateStateKeyValueStorage privateStateStorage;
  private PrivateStateRootResolver privateStateRootResolver;
  private PrivateStorageMigration migration;

  @Before
  public void setUp() {
    final KeyValueStorage kvStorage = new InMemoryKeyValueStorage();

    legacyPrivateStateStorage = new LegacyPrivateStateKeyValueStorage(kvStorage);
    privateStateStorage = new PrivateStateKeyValueStorage(kvStorage);
    privateStateRootResolver = new PrivateStateRootResolver(privateStateStorage);

    migration =
        new PrivateStorageMigration(
            privateStateStorage,
            blockchain,
            enclave,
            ENCLAVE_KEY,
            PRIVACY_ADDRESS,
            migrationTransactionProcessor);
  }

  @Test
  public void migrationShouldGenerateExpectedStateRoot() {
    final Bytes privacyGroupId = Bytes.fromBase64String(PRIVACY_GROUP_ID);
    final Hash expectedStateRoot = createLegacyStateRootEntry(privacyGroupId);
    final Transaction privacyMarkerTransaction = createPrivacyMarkerTransaction();
    createPrivateTransaction(privacyGroupId, privacyMarkerTransaction);
    createPrivateTransactionExecutionResult(expectedStateRoot);

    migration.migratePrivateStorage();

    assertThatLegacyStateRootEntryWasDeleted(privacyGroupId);
    assertThatStateRootResolverReturnsExpectedHashForChainHead(privacyGroupId, expectedStateRoot);
  }

  private void assertThatLegacyStateRootEntryWasDeleted(final Bytes privacyGroupId) {
    assertThat(legacyPrivateStateStorage.getLatestStateRoot(privacyGroupId)).isEmpty();
  }

  private void assertThatStateRootResolverReturnsExpectedHashForChainHead(
      final Bytes privacyGroupId, final Hash expectedStateRoot) {
    assertThat(
            privateStateRootResolver.resolveLastStateRoot(
                Bytes32.wrap(privacyGroupId), blockchain.getChainHeadHash()))
        .isEqualTo(expectedStateRoot);
  }

  @Test
  public void migrationShouldGenerateExpectedPrivateBlockAndTransactionMetadata() {
    final Bytes privacyGroupId = Bytes.fromBase64String(PRIVACY_GROUP_ID);
    final Hash expectedStateRoot = createLegacyStateRootEntry(privacyGroupId);
    final Transaction privacyMarkerTransaction = createPrivacyMarkerTransaction();
    final PrivateTransaction privateTransaction =
        createPrivateTransaction(privacyGroupId, privacyMarkerTransaction);
    createPrivateTransactionExecutionResult(expectedStateRoot);

    createLegacyTransactionMetadata(
        privacyMarkerTransaction, privateTransaction, expectedStateRoot);

    migration.migratePrivateStorage();

    final PrivateBlockMetadata privateBlockMetadata =
        fetchPrivateBlockMetadataAtChainHead(privacyGroupId);
    assertThat(privateBlockMetadata.getLatestStateRoot()).isPresent().hasValue(expectedStateRoot);
    assertThat((privateBlockMetadata.getPrivateTransactionMetadataList())).hasSize(1);

    final PrivateTransactionMetadata privateTransactionMetadata =
        privateBlockMetadata.getPrivateTransactionMetadataList().get(0);
    assertThat(privateTransactionMetadata.getPrivacyMarkerTransactionHash())
        .isEqualTo(privacyMarkerTransaction.getHash());
    assertThat(privateTransactionMetadata.getStateRoot()).isEqualTo(expectedStateRoot);

    assertThatLegacyTransactionMetadataWasDeleted(privateTransaction);
  }

  private PrivateBlockMetadata fetchPrivateBlockMetadataAtChainHead(final Bytes privacyGroupId) {
    return privateStateStorage
        .getPrivateBlockMetadata(blockchain.getChainHeadHash(), Bytes32.wrap(privacyGroupId))
        .orElseThrow();
  }

  private void createLegacyTransactionMetadata(
      final Transaction privacyMarkerTransaction,
      final PrivateTransaction privateTransaction,
      final Hash expectedStateRoot) {
    final PrivateTransactionMetadata previousTransactionMetadata =
        new PrivateTransactionMetadata(privacyMarkerTransaction.getHash(), expectedStateRoot);
    legacyPrivateStateStorage
        .updater()
        .putTransactionMetadata(
            blockchain.getChainHeadHash(),
            privateTransaction.getHash(),
            previousTransactionMetadata)
        .commit();
  }

  private void assertThatLegacyTransactionMetadataWasDeleted(
      final PrivateTransaction privateTransaction) {
    assertThat(
            legacyPrivateStateStorage.getTransactionMetadata(
                blockchain.getChainHeadHash(), privateTransaction.getHash()))
        .isEmpty();
  }

  @Test
  public void migrationShouldGenerateExpectedPrivacyGroupBlockHeadMapping() {
    final Bytes privacyGroupId = Bytes.fromBase64String(PRIVACY_GROUP_ID);
    final Hash expectedStateRoot = createLegacyStateRootEntry(privacyGroupId);
    final Transaction privacyMarkerTransaction = createPrivacyMarkerTransaction();
    createPrivateTransaction(privacyGroupId, privacyMarkerTransaction);
    createPrivateTransactionExecutionResult(expectedStateRoot);

    migration.migratePrivateStorage();

    final PrivacyGroupHeadBlockMap expectedPrivacyGroupHeadBlockMap =
        new PrivacyGroupHeadBlockMap(
            Map.of(Bytes32.wrap(privacyGroupId), blockchain.getChainHeadHash()));
    assertThat(privateStateStorage.getPrivacyGroupHeadBlockMap(blockchain.getChainHeadHash()))
        .isPresent()
        .hasValue(expectedPrivacyGroupHeadBlockMap);
  }

  @Test
  public void migrationShouldGenerateExpectedPrivateTransactionReceipt() {
    final Bytes privacyGroupId = Bytes.fromBase64String(PRIVACY_GROUP_ID);
    final Hash expectedStateRoot = createLegacyStateRootEntry(privacyGroupId);
    final Transaction privacyMarkerTransaction = createPrivacyMarkerTransaction();
    final PrivateTransaction privateTransaction =
        createPrivateTransaction(privacyGroupId, privacyMarkerTransaction);
    final Result privateTransactionExecutionResult =
        createPrivateTransactionExecutionResult(expectedStateRoot);

    createLegacyTransactionResultData(privateTransaction, privateTransactionExecutionResult);

    migration.migratePrivateStorage();

    final PrivateTransactionReceipt expectedPrivateTransactionReceipt =
        new PrivateTransactionReceipt(privateTransactionExecutionResult);

    final PrivateTransactionReceipt actualTransactionReceipt =
        privateStateStorage
            .getTransactionReceipt(blockchain.getChainHeadHash(), privateTransaction.getHash())
            .orElseThrow();

    assertThat(actualTransactionReceipt).isEqualTo(expectedPrivateTransactionReceipt);
    assertThatLegacyTransactionResultDataWasDeleted(privateTransaction);
  }

  private void createLegacyTransactionResultData(
      final PrivateTransaction privateTransaction, final Result privateTransactionExecutionResult) {
    final Updater updater = legacyPrivateStateStorage.updater();
    updater.putTransactionLogs(
        privateTransaction.getHash(), privateTransactionExecutionResult.getLogs());
    updater.putTransactionResult(
        privateTransaction.getHash(), privateTransactionExecutionResult.getOutput());
    updater.putTransactionRevertReason(
        privateTransaction.getHash(),
        privateTransactionExecutionResult.getRevertReason().orElse(Bytes.EMPTY));
    updater.putTransactionStatus(
        privateTransaction.getHash(),
        Bytes.of(
            privateTransactionExecutionResult.getStatus() == Result.Status.SUCCESSFUL ? 1 : 0));
    updater.commit();
  }

  private void assertThatLegacyTransactionResultDataWasDeleted(
      final PrivateTransaction privateTransaction) {
    assertThat(legacyPrivateStateStorage.getTransactionLogs(privateTransaction.getHash()))
        .isEmpty();
    assertThat(legacyPrivateStateStorage.getTransactionOutput(privateTransaction.getHash()))
        .isEmpty();
    assertThat(legacyPrivateStateStorage.getRevertReason(privateTransaction.getHash())).isEmpty();
    assertThat(legacyPrivateStateStorage.getStatus(privateTransaction.getHash())).isEmpty();
  }

  private Hash createLegacyStateRootEntry(final Bytes privacyGroupId) {
    final Hash stateRootHash = Hash.hash(Bytes.random(32));

    legacyPrivateStateStorage.updater().putLatestStateRoot(privacyGroupId, stateRootHash).commit();

    assertThat(legacyPrivateStateStorage.getLatestStateRoot(privacyGroupId))
        .isPresent()
        .hasValue(stateRootHash);

    return stateRootHash;
  }

  private Transaction createPrivacyMarkerTransaction() {
    final Transaction privacyMarkerTransaction = privacyMarkerTransaction(TRANSACTION_KEY);
    mockBlockchainWithPrivacyMarkerTransaction(privacyMarkerTransaction);
    return privacyMarkerTransaction;
  }

  private void mockBlockchainWithPrivacyMarkerTransaction(final Transaction transaction) {
    final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
    final Block genesis = blockDataGenerator.genesisBlock();
    final BlockDataGenerator.BlockOptions options = BlockDataGenerator.BlockOptions.create();
    options.setParentHash(genesis.getHash());
    options.setBlockNumber(1);
    options.addTransaction(transaction);
    final Block block = blockDataGenerator.block(options);

    when(blockchain.getChainHeadBlockNumber()).thenReturn(1L);
    when(blockchain.getChainHeadHash()).thenReturn(block.getHash());
    when(blockchain.getBlockByNumber(0L)).thenReturn(Optional.of(genesis));
    when(blockchain.getBlockByNumber(1L)).thenReturn(Optional.of(block));
  }

  private PrivateTransaction createPrivateTransaction(
      final Bytes privacyGroupId, final Transaction privacyMarkerTransaction) {
    final String transactionKey = privacyMarkerTransaction.getData().orElseThrow().toBase64String();

    final PrivateTransaction privateTransaction =
        privateTransaction(privacyGroupId.toBase64String());

    mockEnclaveWithPrivateTransaction(privacyGroupId, transactionKey, privateTransaction);

    return privateTransaction;
  }

  private void mockEnclaveWithPrivateTransaction(
      final Bytes privacyGroupId,
      final String transactionKey,
      final PrivateTransaction privateTransaction) {

    final Bytes encodedPrivateTransaction = RLP.encode(privateTransaction::writeTo);
    final byte[] payload =
        Base64.getEncoder().encodeToString(encodedPrivateTransaction.toArray()).getBytes(UTF_8);

    when(enclave.receive(eq(transactionKey), eq(ENCLAVE_KEY.toBase64String())))
        .thenReturn(
            new ReceiveResponse(
                payload, privacyGroupId.toBase64String(), ENCLAVE_KEY.toBase64String()));
  }

  private Result createPrivateTransactionExecutionResult(final Hash expectedStateRoot) {
    final PrivateTransactionProcessor.Result privateTransactionExecutionResult =
        successfulPrivateTxProcessingResult();

    final PrivateStorageMigrationTransactionProcessorResult txSimulatorResult =
        new PrivateStorageMigrationTransactionProcessorResult(
            privateTransactionExecutionResult, Optional.of(expectedStateRoot));

    when(migrationTransactionProcessor.process(anyString(), any(PrivateTransaction.class), any()))
        .thenReturn(Optional.of(txSimulatorResult));

    return privateTransactionExecutionResult;
  }
}
