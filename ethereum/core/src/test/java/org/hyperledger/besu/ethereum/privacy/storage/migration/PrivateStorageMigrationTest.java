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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.privateMarkerTransaction;
import static org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver.EMPTY_ROOT_HASH;
import static org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage.SCHEMA_VERSION_1_0_0;
import static org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage.SCHEMA_VERSION_1_4_0;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor.TransactionReceiptFactory;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings({"unchecked", "rawtypes"})
public class PrivateStorageMigrationTest {

  private static final String PRIVACY_GROUP_ID = "tJw12cPM6EZRF5zfHv2zLePL0cqlaDjLn0x1T/V0yzE=";
  public static final Bytes32 PRIVACY_GROUP_BYTES =
      Bytes32.wrap(Bytes.fromBase64String(PRIVACY_GROUP_ID));
  private static final Address PRIVACY_ADDRESS = DEFAULT_PRIVACY;

  @Mock private Blockchain blockchain;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private WorldStateArchive publicWorldStateArchive;
  @Mock private MutableWorldState publicMutableWorldState;
  @Mock private LegacyPrivateStateStorage legacyPrivateStateStorage;
  @Mock private MainnetTransactionProcessor transactionProcessor;
  @Mock private TransactionReceiptFactory transactionReceiptFactory;
  @Mock private MiningBeneficiaryCalculator miningBeneficiaryCalculator;
  @Mock private PrivateMigrationBlockProcessor privateMigrationBlockProcessor;

  private PrivateStateKeyValueStorage privateStateStorage;
  private PrivateStateRootResolver privateStateRootResolver;
  private PrivateStorageMigration migration;

  @Before
  public void setUp() {
    final KeyValueStorage kvStorage = new InMemoryKeyValueStorage();

    privateStateStorage = new PrivateStateKeyValueStorage(kvStorage);
    privateStateRootResolver = new PrivateStateRootResolver(privateStateStorage);

    lenient().when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(protocolSpec);
    lenient().when(protocolSpec.getTransactionProcessor()).thenReturn(transactionProcessor);
    lenient()
        .when(protocolSpec.getTransactionReceiptFactory())
        .thenReturn(transactionReceiptFactory);
    lenient().when(protocolSpec.getBlockReward()).thenReturn(Wei.ZERO);
    lenient()
        .when(protocolSpec.getMiningBeneficiaryCalculator())
        .thenReturn(miningBeneficiaryCalculator);
    lenient().when(protocolSpec.isSkipZeroBlockRewards()).thenReturn(false);

    migration =
        new PrivateStorageMigration(
            blockchain,
            PRIVACY_ADDRESS,
            protocolSchedule,
            publicWorldStateArchive,
            privateStateStorage,
            privateStateRootResolver,
            legacyPrivateStateStorage,
            (protocolSpec) -> privateMigrationBlockProcessor);
  }

  @Test
  public void privateGroupHeadBlocKMapIsCopiedFromPreviousBlocks() {
    mockBlockchainWithZeroTransactions();

    // create existing map at block hash 'zero' (pre-genesis)
    final PrivacyGroupHeadBlockMap existingPgHeadMap =
        createPrivacyGroupHeadBlockInitialMapAndMetadata(PRIVACY_GROUP_BYTES, EMPTY_ROOT_HASH);

    migration.migratePrivateStorage();

    // check that for every block we have the existing mapping
    for (long i = 0; i <= blockchain.getChainHeadBlockNumber(); i++) {
      final Optional<PrivacyGroupHeadBlockMap> pgHeadMapAfterMigration =
          privateStateStorage.getPrivacyGroupHeadBlockMap(
              blockchain.getBlockByNumber(i).get().getHash());

      assertThat(pgHeadMapAfterMigration).isPresent().hasValue(existingPgHeadMap);
    }
  }

  @Test
  public void successfulMigrationBumpsSchemaVersion() {
    final Transaction privateMarkerTransaction = createPrivateMarkerTransaction();
    mockBlockchainWithPrivateMarkerTransaction(privateMarkerTransaction);
    assertThat(privateStateStorage.getSchemaVersion()).isEqualTo(SCHEMA_VERSION_1_0_0);

    migration.migratePrivateStorage();

    assertThat(privateStateStorage.getSchemaVersion()).isEqualTo(SCHEMA_VERSION_1_4_0);
  }

  @Test
  public void failedMigrationThrowsErrorAndDoesNotBumpSchemaVersion() {
    final Transaction privateMarkerTransaction = createPrivateMarkerTransaction();
    mockBlockchainWithPrivateMarkerTransaction(privateMarkerTransaction);
    final Hash rootHashOtherThanZero = Hash.wrap(Bytes32.fromHexStringLenient("1"));
    createPrivacyGroupHeadBlockInitialMapAndMetadata(PRIVACY_GROUP_BYTES, rootHashOtherThanZero);

    // final state root won't match the legacy state root
    when(legacyPrivateStateStorage.getLatestStateRoot(any())).thenReturn(Optional.of(Hash.ZERO));

    assertThat(privateStateStorage.getSchemaVersion()).isEqualTo(SCHEMA_VERSION_1_0_0);

    assertThatThrownBy(() -> migration.migratePrivateStorage())
        .isInstanceOf(PrivateStorageMigrationException.class)
        .hasMessageContaining("Inconsistent state root");

    assertThat(privateStateStorage.getSchemaVersion()).isEqualTo(SCHEMA_VERSION_1_0_0);
  }

  @Test
  public void migrationInBlockchainWithZeroPMTsDoesNotReprocessAnyBlocks() {
    mockBlockchainWithZeroTransactions();

    migration.migratePrivateStorage();

    verifyNoInteractions(privateMigrationBlockProcessor);
  }

  @Test
  public void migrationReprocessBlocksWithPMT() {
    final Transaction privateMarkerTransaction = createPrivateMarkerTransaction();
    mockBlockchainWithPrivateMarkerTransaction(privateMarkerTransaction);
    final Block blockWithPMT = blockchain.getBlockByNumber(1L).orElseThrow();

    migration.migratePrivateStorage();

    verify(privateMigrationBlockProcessor)
        .processBlock(
            any(),
            any(),
            eq(blockWithPMT.getHeader()),
            eq(blockWithPMT.getBody().getTransactions()),
            eq(blockWithPMT.getBody().getOmmers()));
  }

  /*
   When processing a block, we only need to process up to the last PTM in the block.
  */
  @Test
  public void migrationOnlyProcessRequiredTransactions() {
    final List<Transaction> transactions = new ArrayList<>();
    transactions.add(publicTransaction());
    transactions.add(createPrivateMarkerTransaction());
    transactions.add(publicTransaction());

    mockBlockchainWithTransactionsInABlock(transactions);

    migration.migratePrivateStorage();

    final ArgumentCaptor<List> txsCaptor = ArgumentCaptor.forClass(List.class);

    verify(privateMigrationBlockProcessor)
        .processBlock(any(), any(), any(), txsCaptor.capture(), any());

    // won't process transaction after PMT, that's why we only process 2 txs
    final List<Transaction> processedTxs = txsCaptor.getValue();
    assertThat(processedTxs).hasSize(2);
  }

  private PrivacyGroupHeadBlockMap createPrivacyGroupHeadBlockInitialMapAndMetadata(
      final Bytes32 privacyGroupBytes, final Hash rootHash) {
    final PrivacyGroupHeadBlockMap existingPgHeadMap =
        new PrivacyGroupHeadBlockMap(Map.of(privacyGroupBytes, Hash.ZERO));
    final PrivateStateStorage.Updater updater = privateStateStorage.updater();
    updater.putPrivacyGroupHeadBlockMap(Hash.ZERO, existingPgHeadMap);
    updater.putPrivateBlockMetadata(
        Hash.ZERO,
        privacyGroupBytes,
        new PrivateBlockMetadata(
            Arrays.asList(new PrivateTransactionMetadata(Hash.ZERO, rootHash))));
    updater.commit();
    return existingPgHeadMap;
  }

  private Transaction createPrivateMarkerTransaction() {
    final Transaction privateMarkerTransaction = privateMarkerTransaction();
    mockBlockchainWithPrivateMarkerTransaction(privateMarkerTransaction);
    return privateMarkerTransaction;
  }

  private void mockBlockchainWithZeroTransactions() {
    final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

    final Block genesis = blockDataGenerator.genesisBlock();
    mockBlockInBlockchain(genesis);

    final BlockDataGenerator.BlockOptions options =
        BlockDataGenerator.BlockOptions.create()
            .setParentHash(genesis.getHash())
            .setBlockNumber(1)
            .hasTransactions(false);
    final Block block = blockDataGenerator.block(options);
    mockBlockInBlockchain(block);
    mockChainHeadInBlockchain(block);

    when(legacyPrivateStateStorage.getLatestStateRoot(any()))
        .thenReturn(Optional.of(EMPTY_ROOT_HASH));
  }

  private void mockBlockchainWithPrivateMarkerTransaction(final Transaction transaction) {
    final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

    final Block genesis = blockDataGenerator.genesisBlock();
    mockBlockInBlockchain(genesis);

    final BlockDataGenerator.BlockOptions options =
        BlockDataGenerator.BlockOptions.create()
            .setParentHash(genesis.getHash())
            .setBlockNumber(1)
            .addTransaction(transaction);
    final Block block = blockDataGenerator.block(options);
    mockBlockInBlockchain(block);
    mockChainHeadInBlockchain(block);
  }

  private void mockBlockchainWithTransactionsInABlock(final List<Transaction> transactions) {
    final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

    final Block genesis = blockDataGenerator.genesisBlock();
    mockBlockInBlockchain(genesis);

    final Block block =
        blockDataGenerator.block(
            BlockDataGenerator.BlockOptions.create()
                .setParentHash(genesis.getHash())
                .setBlockNumber(1)
                .addTransaction(transactions));
    mockBlockInBlockchain(block);
    mockChainHeadInBlockchain(block);
  }

  private void mockBlockInBlockchain(final Block block) {
    final BlockHeader blockHeader = block.getHeader();
    final Hash blockHash = block.getHash();
    when(blockchain.getBlockByNumber(blockHeader.getNumber())).thenReturn(Optional.of(block));
    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(block.getBody()));

    when(publicWorldStateArchive.getMutable(blockHeader.getStateRoot(), blockHash))
        .thenReturn(Optional.of(publicMutableWorldState));
  }

  private void mockChainHeadInBlockchain(final Block block) {
    when(blockchain.getChainHeadBlockNumber()).thenReturn(block.getHeader().getNumber());
    when(blockchain.getChainHeadHash()).thenReturn(block.getHash());
  }

  private Transaction publicTransaction() {
    return Transaction.builder()
        .type(TransactionType.FRONTIER)
        .nonce(0)
        .gasPrice(Wei.of(1000))
        .gasLimit(3000000)
        .value(Wei.ZERO)
        .payload(Bytes.EMPTY)
        .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
        .chainId(BigInteger.valueOf(1337))
        .signAndBuild(SignatureAlgorithmFactory.getInstance().generateKeyPair());
  }
}
