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
package org.hyperledger.besu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;
import static org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver.EMPTY_ROOT_HASH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.components.BesuComponent;
import org.hyperledger.besu.components.BesuPluginContextModule;
import org.hyperledger.besu.components.EnclaveModule;
import org.hyperledger.besu.components.MockBesuCommandModule;
import org.hyperledger.besu.components.NoOpMetricsSystemModule;
import org.hyperledger.besu.components.PrivacyTestModule;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.InMemoryPrivacyStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCacheModule;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedMerkleTrieLoaderModule;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.data.Restriction;
import org.hyperledger.besu.testutil.TestClock;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.base.Suppliers;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("rawtypes")
public class PrivacyReorgTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private static final KeyPair KEY_PAIR =
      SIGNATURE_ALGORITHM
          .get()
          .createKeyPair(
              SIGNATURE_ALGORITHM
                  .get()
                  .createPrivateKey(
                      new BigInteger(
                          "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));
  private static final Bytes ENCLAVE_PUBLIC_KEY =
      Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

  private static final String FIRST_BLOCK_WITH_NO_TRANSACTIONS_STATE_ROOT =
      "0xb0784ff11dffceac824188583f20f3b8bb4ca275e033b3b1c0e280915743be7f";
  private static final String FIRST_BLOCK_WITH_SINGLE_TRANSACTION_STATE_ROOT =
      "0xe33629724501c0bc271a2b6858da64d3e92048d7e0cd019c5646770330694ff4";
  private static final String BLOCK_WITH_SINGLE_TRANSACTION_RECEIPTS_ROOT =
      "0xc8267b3f9ed36df3ff8adb51a6d030716f23eeb50270e7fce8d9822ffa7f0461";
  private static final String STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMPTY_STATE =
      "0x2121b68f1333e93bae8cd717a3ca68c9d7e7003f6b288c36dfc59b0f87be9590";
  private static final Bytes32 PRIVACY_GROUP_BYTES32 =
      Bytes32.fromHexString("0xf250d523ae9164722b06ca25cfa2a7f3c45df96b09e215236f886c876f715bfa");
  private static final Bytes32 PRIVACY_TRANSACTION_PAYLOAD =
      Bytes32.fromHexString("0xa250d523ae9164722b06ca25cfa2a7f3c45df96b09e215236f886c876f715bfa");

  // EventEmitter contract binary
  private static final Bytes MOCK_PAYLOAD =
      Bytes.fromHexString(
          "0x608060405234801561001057600080fd5b5060008054600160a060020a03191633179055610199806100326000396000f3fe6080604052600436106100565763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f245811461005b5780636057361d1461008257806367e404ce146100ae575b600080fd5b34801561006757600080fd5b506100706100ec565b60408051918252519081900360200190f35b34801561008e57600080fd5b506100ac600480360360208110156100a557600080fd5b50356100f2565b005b3480156100ba57600080fd5b506100c3610151565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200190f35b60025490565b604080513381526020810183905281517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5929181900390910190a16002556001805473ffffffffffffffffffffffffffffffffffffffff191633179055565b60015473ffffffffffffffffffffffffffffffffffffffff169056fea165627a7a72305820c7f729cb24e05c221f5aa913700793994656f233fe2ce3b9fd9a505ea17e8d8a0029");
  private static final PrivateTransaction PRIVATE_TRANSACTION =
      PrivateTransaction.builder()
          .chainId(BigInteger.valueOf(1337))
          .gasLimit(1000)
          .gasPrice(Wei.ZERO)
          .nonce(0)
          .payload(MOCK_PAYLOAD)
          .to(null)
          .privateFrom(ENCLAVE_PUBLIC_KEY)
          .privateFor(Collections.singletonList(ENCLAVE_PUBLIC_KEY))
          .restriction(Restriction.RESTRICTED)
          .value(Wei.ZERO)
          .signAndBuild(KEY_PAIR);

  private final BlockDataGenerator gen = new BlockDataGenerator();
  private PrivacyParameters privacyParameters;
  private Enclave mockEnclave;
  private Transaction privacyMarkerTransaction;
  private final PrivacyReorgTestComponent component =
      DaggerPrivacyReorgTest_PrivacyReorgTestComponent.create();

  private final BesuController besuController = component.getBesuController();
  private final PrivateStateRootResolver privateStateRootResolver =
      component.getPrivacyParameters().getPrivateStateRootResolver();

  @BeforeEach
  public void setUp() throws IOException {
    mockEnclave = mock(Enclave.class);
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    PRIVATE_TRANSACTION.writeTo(rlpOutput);

    when(mockEnclave.receive(any()))
        .thenReturn(
            new ReceiveResponse(
                rlpOutput.encoded().toBase64String().getBytes(StandardCharsets.UTF_8),
                PRIVACY_GROUP_BYTES32.toBase64String(),
                ENCLAVE_PUBLIC_KEY.toBase64String()));

    privacyMarkerTransaction =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
            .chainId(BigInteger.valueOf(1337))
            .gasLimit(60000)
            .gasPrice(Wei.of(1000))
            .nonce(0)
            .payload(PRIVACY_TRANSACTION_PAYLOAD)
            .to(DEFAULT_PRIVACY)
            .value(Wei.ZERO)
            .signAndBuild(KEY_PAIR);

    // Configure Privacy
    EnclaveFactory enclaveFactory = mock(EnclaveFactory.class);
    when(enclaveFactory.createVertxEnclave(any())).thenReturn(mockEnclave);

    privacyParameters =
        new PrivacyParameters.Builder()
            .setEnabled(true)
            .setStorageProvider(createKeyValueStorageProvider())
            .setEnclaveUrl(URI.create("http//1.1.1.1:1234"))
            .setEnclaveFactory(enclaveFactory)
            .build();

    privacyParameters.setPrivacyUserId(ENCLAVE_PUBLIC_KEY.toBase64String());
  }

  @Test
  public void privacyGroupHeadIsTracked() {
    // Setup an initial blockchain with one private transaction
    final ProtocolContext protocolContext = besuController.getProtocolContext();
    final DefaultBlockchain blockchain = (DefaultBlockchain) protocolContext.getBlockchain();
    final PrivateStateStorage privateStateStorage =
        component.getPrivacyParameters().getPrivateStateStorage();

    final Block firstBlock =
        gen.block(
            getBlockOptionsWithTransaction(
                blockchain.getGenesisBlock(),
                privacyMarkerTransaction,
                FIRST_BLOCK_WITH_SINGLE_TRANSACTION_STATE_ROOT));

    appendBlock(besuController, blockchain, protocolContext, firstBlock);

    final PrivacyGroupHeadBlockMap expected =
        new PrivacyGroupHeadBlockMap(
            Collections.singletonMap(PRIVACY_GROUP_BYTES32, firstBlock.getHash()));

    assertThat(
            privateStateStorage.getPrivacyGroupHeadBlockMap(blockchain.getGenesisBlock().getHash()))
        .isEmpty();
    assertThat(privateStateStorage.getPrivacyGroupHeadBlockMap(firstBlock.getHash())).isNotEmpty();
    assertThat(privateStateStorage.getPrivacyGroupHeadBlockMap(firstBlock.getHash()))
        .contains(expected);

    final String secondBlockStateRoot =
        "0x57a19f52a9ff4405428b3e605e136662d5c1b6be6f84f98f3b8c42ddac5139c2";
    final Block secondBlock =
        gen.block(getBlockOptionsNoTransaction(firstBlock, secondBlockStateRoot));

    appendBlock(besuController, blockchain, protocolContext, secondBlock);

    assertThat(privateStateStorage.getPrivacyGroupHeadBlockMap(secondBlock.getHash())).isNotEmpty();

    assertThat(privateStateStorage.getPrivacyGroupHeadBlockMap(secondBlock.getHash()))
        .contains(expected);
  }

  @Test
  public void reorgToChainAtEqualHeight() {
    // Setup an initial blockchain with one private transaction
    final ProtocolContext protocolContext = besuController.getProtocolContext();
    final DefaultBlockchain blockchain = (DefaultBlockchain) protocolContext.getBlockchain();
    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(0);
    final Block firstBlock =
        gen.block(
            getBlockOptionsWithTransaction(
                blockchain.getGenesisBlock(),
                privacyMarkerTransaction,
                FIRST_BLOCK_WITH_SINGLE_TRANSACTION_STATE_ROOT));

    var importResult = appendBlock(besuController, blockchain, protocolContext, firstBlock);
    assertThat(importResult.isImported()).isTrue();
    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(1);
    // Check that the private state root is not the empty state
    assertPrivateStateRoot(
        privateStateRootResolver, blockchain, STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMPTY_STATE);

    // Create parallel fork of length 1 which removes privacy marker transaction
    final Block forkBlock =
        gen.block(
            getBlockOptionsNoTransactionWithDifficulty(
                blockchain.getGenesisBlock(),
                blockchain.getChainHeadHeader().getDifficulty().plus(10L),
                FIRST_BLOCK_WITH_NO_TRANSACTIONS_STATE_ROOT));

    appendBlock(besuController, blockchain, protocolContext, forkBlock);

    // Check that the private state root is the empty state
    assertPrivateStateRoot(privateStateRootResolver, blockchain, EMPTY_ROOT_HASH);
  }

  @Test
  public void reorgToShorterChain() {
    // Setup an initial blockchain with one private transaction
    final ProtocolContext protocolContext = besuController.getProtocolContext();
    final DefaultBlockchain blockchain = (DefaultBlockchain) protocolContext.getBlockchain();

    final String firstBlockStateRoot =
        "0xbca927086c294984d6c2add82731c386cf2df3cd75509907dac928de12b7c472";
    final Block firstBlock =
        gen.block(getBlockOptionsNoTransaction(blockchain.getGenesisBlock(), firstBlockStateRoot));

    final String secondBlockStateRoot =
        "0xb3d70bce4428fb9b4549240b2130c4eea12c4ea36ae13108ed21289366a8d65f";
    final Block secondBlock =
        gen.block(
            getBlockOptionsWithTransaction(
                firstBlock, privacyMarkerTransaction, secondBlockStateRoot));

    appendBlock(besuController, blockchain, protocolContext, firstBlock);
    appendBlock(besuController, blockchain, protocolContext, secondBlock);

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(2);

    // Check that the private state root is not the empty state
    assertPrivateStateRoot(
        privateStateRootResolver, blockchain, STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMPTY_STATE);

    // Create parallel fork of length 1 which removes privacy marker transaction
    final Difficulty remainingDifficultyToOutpace =
        blockchain
            .getBlockByNumber(1)
            .get()
            .getHeader()
            .getDifficulty()
            .plus(blockchain.getBlockByNumber(2).get().getHeader().getDifficulty());

    final String forkBlockStateRoot =
        "0x5c0adcdde38d38b4365c238c4ba05bf9ebfdf506f749b884b67003f375e43e4b";
    final Block forkBlock =
        gen.block(
            getBlockOptionsNoTransactionWithDifficulty(
                blockchain.getGenesisBlock(),
                remainingDifficultyToOutpace.plus(10L),
                forkBlockStateRoot));

    appendBlock(besuController, blockchain, protocolContext, forkBlock);

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(1);

    // Check that the private state root is the empty state
    assertPrivateStateRoot(privateStateRootResolver, blockchain, EMPTY_ROOT_HASH);
  }

  @Test
  public void reorgToLongerChain() {
    // Setup an initial blockchain with one private transaction
    final ProtocolContext protocolContext = besuController.getProtocolContext();
    final DefaultBlockchain blockchain = (DefaultBlockchain) protocolContext.getBlockchain();

    final Block firstBlock =
        gen.block(
            getBlockOptionsWithTransaction(
                blockchain.getGenesisBlock(),
                privacyMarkerTransaction,
                FIRST_BLOCK_WITH_SINGLE_TRANSACTION_STATE_ROOT));

    appendBlock(besuController, blockchain, protocolContext, firstBlock);

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(1);

    // Check that the private state root is not the empty state
    assertPrivateStateRoot(
        privateStateRootResolver, blockchain, STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMPTY_STATE);

    // Create parallel fork of length 1 which removes privacy marker transaction
    final Block forkBlock =
        gen.block(
            getBlockOptionsNoTransactionWithDifficulty(
                blockchain.getGenesisBlock(),
                firstBlock.getHeader().getDifficulty().plus(10L),
                FIRST_BLOCK_WITH_NO_TRANSACTIONS_STATE_ROOT));

    // Check that the private state root did not change
    assertPrivateStateRoot(
        privateStateRootResolver, blockchain, STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMPTY_STATE);

    final String secondForkBlockStateRoot =
        "0x76b8747d05fabcc87b2cfba519da0b1359b29fe7553bfd4837746edf31fb95fc";
    final Block secondForkBlock =
        gen.block(
            getBlockOptionsNoTransactionWithDifficulty(
                forkBlock,
                forkBlock.getHeader().getDifficulty().plus(10L),
                secondForkBlockStateRoot));

    appendBlock(besuController, blockchain, protocolContext, forkBlock);
    appendBlock(besuController, blockchain, protocolContext, secondForkBlock);

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(2);

    // Check that the private state root is the empty state
    assertPrivateStateRoot(privateStateRootResolver, blockchain, EMPTY_ROOT_HASH);

    // Add another private transaction
    final String thirdForkBlockStateRoot =
        "0xcae9fa05107c1501c1962239f729d2f34186414abbaeb0dd1a3e0a6c899f79a3";
    final Block thirdForkBlock =
        gen.block(
            getBlockOptionsWithTransactionAndDifficulty(
                secondForkBlock,
                privacyMarkerTransaction,
                secondForkBlock.getHeader().getDifficulty().plus(10L),
                thirdForkBlockStateRoot));

    appendBlock(besuController, blockchain, protocolContext, thirdForkBlock);

    // Check that the private state did change after reorg
    assertPrivateStateRoot(
        privateStateRootResolver, blockchain, STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMPTY_STATE);
  }

  @SuppressWarnings("unchecked")
  private BlockImportResult appendBlock(
      final BesuController besuController,
      final DefaultBlockchain blockchain,
      final ProtocolContext protocolContext,
      final Block block) {
    return besuController
        .getProtocolSchedule()
        .getByBlockHeader(blockchain.getChainHeadHeader())
        .getBlockImporter()
        .importBlock(protocolContext, block, HeaderValidationMode.NONE);
  }

  private PrivacyStorageProvider createKeyValueStorageProvider() {
    return new InMemoryPrivacyStorageProvider();
  }

  private void assertPrivateStateRoot(
      final PrivateStateRootResolver privateStateRootResolver,
      final DefaultBlockchain blockchain,
      final String expected) {
    assertPrivateStateRoot(privateStateRootResolver, blockchain, Hash.fromHexString(expected));
  }

  private void assertPrivateStateRoot(
      final PrivateStateRootResolver privateStateRootResolver,
      final DefaultBlockchain blockchain,
      final Hash expected) {
    assertThat(
            privateStateRootResolver.resolveLastStateRoot(
                Bytes32.wrap(
                    Bytes.fromBase64String("8lDVI66RZHIrBsolz6Kn88Rd+WsJ4hUjb4hsh29xW/o=")),
                blockchain.getChainHeadHash()))
        .isEqualTo(expected);
  }

  private BlockDataGenerator.BlockOptions getBlockOptionsNoTransaction(
      final Block parentBlock, final String stateRoot) {
    return getBlockOptions(
        new BlockDataGenerator.BlockOptions()
            .hasTransactions(false)
            .setReceiptsRoot(PrivateStateRootResolver.EMPTY_ROOT_HASH)
            .setGasUsed(0)
            .setStateRoot(Hash.fromHexString(stateRoot)),
        parentBlock);
  }

  private BlockDataGenerator.BlockOptions getBlockOptionsWithTransaction(
      final Block parentBlock, final Transaction transaction, final String stateRoot) {
    return getBlockOptions(
        new BlockDataGenerator.BlockOptions()
            .addTransaction(transaction)
            .setReceiptsRoot(Hash.fromHexString(BLOCK_WITH_SINGLE_TRANSACTION_RECEIPTS_ROOT))
            .setGasUsed(23176)
            .setStateRoot(Hash.fromHexString(stateRoot)),
        parentBlock);
  }

  private BlockDataGenerator.BlockOptions getBlockOptionsNoTransactionWithDifficulty(
      final Block parentBlock, final Difficulty difficulty, final String stateRoot) {
    return getBlockOptions(
        new BlockDataGenerator.BlockOptions()
            .hasTransactions(false)
            .setDifficulty(difficulty)
            .setReceiptsRoot(PrivateStateRootResolver.EMPTY_ROOT_HASH)
            .setGasUsed(0)
            .setStateRoot(Hash.fromHexString(stateRoot)),
        parentBlock);
  }

  private BlockDataGenerator.BlockOptions getBlockOptionsWithTransactionAndDifficulty(
      final Block parentBlock,
      final Transaction transaction,
      final Difficulty difficulty,
      final String stateRoot) {
    return getBlockOptions(
        new BlockDataGenerator.BlockOptions()
            .addTransaction(transaction)
            .setDifficulty(difficulty)
            .setReceiptsRoot(Hash.fromHexString(BLOCK_WITH_SINGLE_TRANSACTION_RECEIPTS_ROOT))
            .setGasUsed(23176)
            .setStateRoot(Hash.fromHexString(stateRoot)),
        parentBlock);
  }

  private BlockDataGenerator.BlockOptions getBlockOptions(
      final BlockDataGenerator.BlockOptions blockOptions, final Block parentBlock) {
    return blockOptions
        .setBaseFee(Optional.empty())
        .setBlockNumber(parentBlock.getHeader().getNumber() + 1)
        .setParentHash(parentBlock.getHash())
        .hasOmmers(false)
        .setLogsBloom(LogsBloomFilter.empty());
  }

  @Singleton
  @Component(
      modules = {
        PrivacyReorgTest.PrivacyReorgParametersModule.class,
        PrivacyReorgTest.PrivacyReorgTestBesuControllerModule.class,
        PrivacyReorgTest.PrivacyReorgTestGenesisConfigModule.class,
        EnclaveModule.class,
        PrivacyTestModule.class,
        MockBesuCommandModule.class,
        NoOpMetricsSystemModule.class,
        BonsaiCachedMerkleTrieLoaderModule.class,
        BlobCacheModule.class,
        BesuPluginContextModule.class
      })
  interface PrivacyReorgTestComponent extends BesuComponent {

    BesuController getBesuController();

    PrivacyParameters getPrivacyParameters();
  }

  @Module
  static class PrivacyReorgParametersModule {

    // TODO: copypasta, get this from the enclave factory
    private static final Bytes ENCLAVE_PUBLIC_KEY =
        Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

    @Provides
    PrivacyParameters providePrivacyReorgParameters(
        final PrivacyStorageProvider storageProvider, final EnclaveFactory enclaveFactory) {

      PrivacyParameters retval =
          new PrivacyParameters.Builder()
              .setEnabled(true)
              .setStorageProvider(storageProvider)
              .setEnclaveUrl(URI.create("http//1.1.1.1:1234"))
              .setEnclaveFactory(enclaveFactory)
              .build();
      retval.setPrivacyUserId(ENCLAVE_PUBLIC_KEY.toBase64String());
      return retval;
    }
  }

  @Module
  static class PrivacyReorgTestBesuControllerModule {

    @Provides
    @Singleton
    @SuppressWarnings("CloseableProvides")
    BesuController provideBesuController(
        final PrivacyParameters privacyParameters,
        final GenesisConfig genesisConfig,
        final PrivacyReorgTestComponent context,
        final @Named("dataDir") Path dataDir) {

      // dataStorageConfiguration default
      // named privacyReorgParams
      BesuController retval =
          new BesuController.Builder()
              .fromGenesisFile(genesisConfig, SyncMode.FULL)
              .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
              .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
              .storageProvider(new InMemoryKeyValueStorageProvider())
              .networkId(BigInteger.ONE)
              .miningParameters(MiningConfiguration.newDefault())
              .nodeKey(NodeKeyUtils.generate())
              .metricsSystem(new NoOpMetricsSystem())
              .dataDirectory(dataDir)
              .clock(TestClock.fixed())
              .privacyParameters(privacyParameters)
              .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
              .gasLimitCalculator(GasLimitCalculator.constant())
              .evmConfiguration(EvmConfiguration.DEFAULT)
              .networkConfiguration(NetworkingConfiguration.create())
              .besuComponent(context)
              .apiConfiguration(ImmutableApiConfiguration.builder().build())
              .build();
      return retval;
    }
  }

  @Module
  static class PrivacyReorgTestGenesisConfigModule {
    @Provides
    GenesisConfig providePrivacyReorgGenesisConfig() {
      return GenesisConfig.fromResource("/privacy_reorg_genesis.json");
    }
  }
}
