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
import static org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver.EMPTY_ROOT_HASH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.InMemoryPrivacyStorageProvider;
import org.hyperledger.besu.ethereum.core.InMemoryStorageProvider;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.MiningParametersTestBuilder;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.privacy.DefaultPrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.orion.testutil.OrionKeyConfiguration;
import org.hyperledger.orion.testutil.OrionTestHarness;
import org.hyperledger.orion.testutil.OrionTestHarnessFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

@SuppressWarnings("rawtypes")
public class PrivacyReorgTest {
  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private static final SECP256K1.KeyPair KEY_PAIR =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));
  private static final Bytes ENCLAVE_PUBLIC_KEY =
      Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

  private static final String FIRST_BLOCK_WITH_NO_TRANSACTIONS_STATE_ROOT =
      "0x1bdf13f6d14c7322d6e695498aab258949e55574bef7eac366eb777f43d7dd2b";
  private static final String FIRST_BLOCK_WITH_SINGLE_TRANSACTION_STATE_ROOT =
      "0x16979b290f429e06d86a43584c7d8689d4292ade9a602e5c78e2867c6ebd904e";
  private static final String BLOCK_WITH_SINGLE_TRANSACTION_RECEIPTS_ROOT =
      "0xc8267b3f9ed36df3ff8adb51a6d030716f23eeb50270e7fce8d9822ffa7f0461";
  private static final String STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMTPY_STATE =
      "0x2121b68f1333e93bae8cd717a3ca68c9d7e7003f6b288c36dfc59b0f87be9590";
  private static final Bytes32 PRIVACY_GROUP_BYTES32 =
      Bytes32.fromHexString("0xf250d523ae9164722b06ca25cfa2a7f3c45df96b09e215236f886c876f715bfa");

  // EventEmitter contract binary
  private static final Bytes MOCK_PAYLOAD =
      Bytes.fromHexString(
          "0x608060405234801561001057600080fd5b5060008054600160a060020a03191633179055610199806100326000396000f3fe6080604052600436106100565763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f245811461005b5780636057361d1461008257806367e404ce146100ae575b600080fd5b34801561006757600080fd5b506100706100ec565b60408051918252519081900360200190f35b34801561008e57600080fd5b506100ac600480360360208110156100a557600080fd5b50356100f2565b005b3480156100ba57600080fd5b506100c3610151565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200190f35b60025490565b604080513381526020810183905281517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5929181900390910190a16002556001805473ffffffffffffffffffffffffffffffffffffffff191633179055565b60015473ffffffffffffffffffffffffffffffffffffffff169056fea165627a7a72305820c7f729cb24e05c221f5aa913700793994656f233fe2ce3b9fd9a505ea17e8d8a0029");
  private static final PrivateTransaction PRIVATE_TRANSACTION =
      PrivateTransaction.builder()
          .chainId(BigInteger.valueOf(2018))
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
  private BesuController besuController;
  private OrionTestHarness enclave;
  private PrivateStateRootResolver privateStateRootResolver;
  private PrivacyParameters privacyParameters;
  private DefaultPrivacyController privacyController;

  @Before
  public void setUp() throws IOException {
    // Start Enclave
    enclave =
        OrionTestHarnessFactory.create(
            folder.newFolder().toPath(),
            new OrionKeyConfiguration("enclavePublicKey", "enclavePrivateKey"));
    enclave.start();

    // Create Storage
    final Path dataDir = folder.newFolder().toPath();

    // Configure Privacy
    privacyParameters =
        new PrivacyParameters.Builder()
            .setEnabled(true)
            .setStorageProvider(createKeyValueStorageProvider())
            .setEnclaveUrl(enclave.clientUrl())
            .setEnclaveFactory(new EnclaveFactory(Vertx.vertx()))
            .build();
    privacyParameters.setEnclavePublicKey(ENCLAVE_PUBLIC_KEY.toBase64String());
    privacyController = mock(DefaultPrivacyController.class);
    when(privacyController.findOffChainPrivacyGroupByGroupId(any(), any()))
        .thenReturn(Optional.of(new PrivacyGroup()));

    privateStateRootResolver =
        new PrivateStateRootResolver(privacyParameters.getPrivateStateStorage());

    besuController =
        new BesuController.Builder()
            .fromGenesisConfig(GenesisConfigFile.development())
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryStorageProvider())
            .networkId(BigInteger.ONE)
            .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
            .nodeKey(NodeKeyUtils.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .privacyParameters(privacyParameters)
            .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
            .gasLimitCalculator(GasLimitCalculator.constant())
            .build();
  }

  @Test
  public void privacyGroupHeadIsTracked() {
    // Setup an initial blockchain with one private transaction
    final ProtocolContext protocolContext = besuController.getProtocolContext();
    final DefaultBlockchain blockchain = (DefaultBlockchain) protocolContext.getBlockchain();
    final PrivateStateStorage privateStateStorage = privacyParameters.getPrivateStateStorage();

    final Transaction privacyMarkerTransaction =
        buildMarkerTransaction(getEnclaveKey(enclave.clientUrl()));
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
        "0xd86a520e49caf215e7e4028262924db50540a5b26e415ab7c944e46a0c01d704";
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

    final Block firstBlock =
        gen.block(
            getBlockOptionsWithTransaction(
                blockchain.getGenesisBlock(),
                buildMarkerTransaction(getEnclaveKey(enclave.clientUrl())),
                FIRST_BLOCK_WITH_SINGLE_TRANSACTION_STATE_ROOT));

    appendBlock(besuController, blockchain, protocolContext, firstBlock);

    // Check that the private state root is not the empty state
    assertPrivateStateRoot(
        privateStateRootResolver, blockchain, STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMTPY_STATE);

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
        "0x35c315ee7d272e5b612d454ee87c948657310ab33208b57122f8d0525e91f35e";
    final Block secondBlock =
        gen.block(
            getBlockOptionsWithTransaction(
                firstBlock,
                buildMarkerTransaction(getEnclaveKey(enclave.clientUrl())),
                secondBlockStateRoot));

    appendBlock(besuController, blockchain, protocolContext, firstBlock);
    appendBlock(besuController, blockchain, protocolContext, secondBlock);

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(2);

    // Check that the private state root is not the empty state
    assertPrivateStateRoot(
        privateStateRootResolver, blockchain, STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMTPY_STATE);

    // Create parallel fork of length 1 which removes privacy marker transaction
    final Difficulty remainingDifficultyToOutpace =
        blockchain
            .getBlockByNumber(1)
            .get()
            .getHeader()
            .getDifficulty()
            .plus(blockchain.getBlockByNumber(2).get().getHeader().getDifficulty());

    final String forkBlockStateRoot =
        "0x4a33bdf9d16e6dd4f4c67f1638971f663f132ebceac0c7c65c9a3f35172af4de";
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
                buildMarkerTransaction(getEnclaveKey(enclave.clientUrl())),
                FIRST_BLOCK_WITH_SINGLE_TRANSACTION_STATE_ROOT));

    appendBlock(besuController, blockchain, protocolContext, firstBlock);

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(1);

    // Check that the private state root is not the empty state
    assertPrivateStateRoot(
        privateStateRootResolver, blockchain, STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMTPY_STATE);

    // Create parallel fork of length 1 which removes privacy marker transaction
    final Block forkBlock =
        gen.block(
            getBlockOptionsNoTransactionWithDifficulty(
                blockchain.getGenesisBlock(),
                firstBlock.getHeader().getDifficulty().plus(10L),
                FIRST_BLOCK_WITH_NO_TRANSACTIONS_STATE_ROOT));

    // Check that the private state root did not change
    assertPrivateStateRoot(
        privateStateRootResolver, blockchain, STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMTPY_STATE);

    final String secondForkBlockStateRoot =
        "0xd35eea814b8b5a0b12e690ab320785f3a33d9685bbf6875637c40a64203915da";
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
        "0xe22344ade05260177b79dcc6c4fed8f87ab95a506c2a6147631ac6547cf44846";
    final Block thirdForkBlock =
        gen.block(
            getBlockOptionsWithTransactionAndDifficulty(
                secondForkBlock,
                buildMarkerTransaction(getEnclaveKey(enclave.clientUrl())),
                secondForkBlock.getHeader().getDifficulty().plus(10L),
                thirdForkBlockStateRoot));

    appendBlock(besuController, blockchain, protocolContext, thirdForkBlock);

    // Check that the private state did change after reorg
    assertPrivateStateRoot(
        privateStateRootResolver, blockchain, STATE_ROOT_AFTER_TRANSACTION_APPENDED_TO_EMTPY_STATE);
  }

  @SuppressWarnings("unchecked")
  private void appendBlock(
      final BesuController besuController,
      final DefaultBlockchain blockchain,
      final ProtocolContext protocolContext,
      final Block block) {
    besuController
        .getProtocolSchedule()
        .getByBlockNumber(blockchain.getChainHeadBlockNumber())
        .getBlockImporter()
        .importBlock(protocolContext, block, HeaderValidationMode.NONE);
  }

  private PrivacyStorageProvider createKeyValueStorageProvider() {
    return new InMemoryPrivacyStorageProvider();
  }

  private Bytes getEnclaveKey(final URI enclaveURI) {
    final Enclave enclave = new EnclaveFactory(Vertx.vertx()).createVertxEnclave(enclaveURI);
    final SendResponse sendResponse =
        sendRequest(enclave, PRIVATE_TRANSACTION, ENCLAVE_PUBLIC_KEY.toBase64String());
    final Bytes payload = Bytes.fromBase64String(sendResponse.getKey());

    // If the key has 0 bytes generate a new key.
    // This is to keep the gasUsed constant allowing
    // hard-coded receipt roots in the block headers
    for (int i = 0; i < payload.size(); i++) {
      if (payload.get(i) == 0) {
        return getEnclaveKey(enclaveURI);
      }
    }

    return payload;
  }

  private SendResponse sendRequest(
      final Enclave enclave,
      final PrivateTransaction privateTransaction,
      final String enclavePublicKey) {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    privateTransaction.writeTo(rlpOutput);
    final String payload = rlpOutput.encoded().toBase64String();

    if (privateTransaction.getPrivacyGroupId().isPresent()) {
      return enclave.send(
          payload, enclavePublicKey, privateTransaction.getPrivacyGroupId().get().toBase64String());
    } else {
      final List<String> privateFor =
          privateTransaction.getPrivateFor().get().stream()
              .map(Bytes::toBase64String)
              .collect(Collectors.toList());

      if (privateFor.isEmpty()) {
        privateFor.add(privateTransaction.getPrivateFrom().toBase64String());
      }
      return enclave.send(
          payload, privateTransaction.getPrivateFrom().toBase64String(), privateFor);
    }
  }

  private Transaction buildMarkerTransaction(final Bytes payload) {
    return Transaction.builder()
        .chainId(BigInteger.valueOf(2018))
        .gasLimit(60000)
        .gasPrice(Wei.of(1000))
        .nonce(0)
        .payload(payload)
        .to(Address.DEFAULT_PRIVACY)
        .value(Wei.ZERO)
        .signAndBuild(KEY_PAIR);
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
        .setBlockNumber(parentBlock.getHeader().getNumber() + 1)
        .setParentHash(parentBlock.getHash())
        .hasOmmers(false)
        .setLogsBloom(LogsBloomFilter.empty());
  }
}
