/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.parallelization.MainnetParallelBlockProcessor;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

@SuppressWarnings("rawtypes")
class AbstractBlockProcessorIntegrationTest {

  private static final String ACCOUNT_GENESIS_1 = "0x627306090abab3a6e1400e9345bc60c78a8bef57";
  private static final String ACCOUNT_GENESIS_2 = "0x7f2d653f56ea8de6ffa554c7a0cd4e03af79f3eb";

  private static final String ACCOUNT_2 = "0x0000000000000000000000000000000000000002";
  private static final String ACCOUNT_3 = "0x0000000000000000000000000000000000000003";
  private static final String ACCOUNT_4 = "0x0000000000000000000000000000000000000004";
  private static final String ACCOUNT_5 = "0x0000000000000000000000000000000000000005";
  private static final String ACCOUNT_6 = "0x0000000000000000000000000000000000000006";
  private static final String CONTRACT_ADDRESS = "0x00000000000000000000000000000000000fffff";

  private static final KeyPair ACCOUNT_GENESIS_1_KEYPAIR =
      generateKeyPair("c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3");

  private static final KeyPair ACCOUNT_GENESIS_2_KEYPAIR =
      generateKeyPair("fc5141e75bf622179f8eedada7fab3e2e6b3e3da8eb9df4f46d84df22df7430e");

  private WorldStateArchive worldStateArchive;
  private DefaultBlockchain blockchain;
  private Address coinbase;

  @BeforeEach
  public void setUp() {
    final ExecutionContextTestFixture contextTestFixture =
        ExecutionContextTestFixture.builder(
                GenesisConfigFile.fromResource(
                    "/org/hyperledger/besu/ethereum/mainnet/genesis-bp-it.json"))
            .dataStorageFormat(DataStorageFormat.BONSAI)
            .build();
    final BlockHeader blockHeader = new BlockHeaderTestFixture().number(0L).buildHeader();
    coinbase = blockHeader.getCoinbase();
    worldStateArchive = contextTestFixture.getStateArchive();
    blockchain = (DefaultBlockchain) contextTestFixture.getBlockchain();
  }

  private static Stream<Arguments> blockProcessorProvider() {
    final ExecutionContextTestFixture contextTestFixture =
        ExecutionContextTestFixture.builder(
                GenesisConfigFile.fromResource(
                    "/org/hyperledger/besu/ethereum/mainnet/genesis-bp-it.json"))
            .dataStorageFormat(DataStorageFormat.BONSAI)
            .build();
    final ProtocolSchedule protocolSchedule = contextTestFixture.getProtocolSchedule();
    final BlockHeader blockHeader = new BlockHeaderTestFixture().number(0L).buildHeader();
    final MainnetTransactionProcessor transactionProcessor =
        protocolSchedule.getByBlockHeader(blockHeader).getTransactionProcessor();

    final BlockProcessor sequentialBlockProcessor =
        new MainnetBlockProcessor(
            transactionProcessor,
            protocolSchedule
                .getByBlockHeader(new BlockHeaderTestFixture().number(0L).buildHeader())
                .getTransactionReceiptFactory(),
            Wei.of(2_000_000_000_000_000L),
            BlockHeader::getCoinbase,
            false,
            protocolSchedule);

    final BlockProcessor parallelBlockProcessor =
        new MainnetParallelBlockProcessor(
            transactionProcessor,
            protocolSchedule
                .getByBlockHeader(new BlockHeaderTestFixture().number(0L).buildHeader())
                .getTransactionReceiptFactory(),
            Wei.of(2_000_000_000_000_000L),
            BlockHeader::getCoinbase,
            false,
            protocolSchedule,
            new NoOpMetricsSystem());

    return Stream.of(
        Arguments.of("sequential", sequentialBlockProcessor),
        Arguments.of("parallel", parallelBlockProcessor));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("blockProcessorProvider")
  void testBlockProcessingWithTransfers(
      final String ignoredName, final BlockProcessor blockProcessor) {
    processSimpleTransfers(blockProcessor);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("blockProcessorProvider")
  void testProcessConflictedSimpleTransfersSameSender(
      final String ignoredName, final BlockProcessor blockProcessor) {
    processConflictedSimpleTransfersSameSender(blockProcessor);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("blockProcessorProvider")
  void testProcessConflictedSimpleTransfersSameAddressReceiverAndSender(
      final String ignoredName, final BlockProcessor blockProcessor) {
    processConflictedSimpleTransfersSameAddressReceiverAndSender(blockProcessor);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("blockProcessorProvider")
  void testProcessConflictedSimpleTransfersWithCoinbase(
      final String ignoredName, final BlockProcessor blockProcessor) {
    processConflictedSimpleTransfersWithCoinbase(blockProcessor);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("blockProcessorProvider")
  void testProcessContractSlotUpdateThenReadTx(
      final String ignoredName, final BlockProcessor blockProcessor) {
    processContractSlotUpdateThenReadTx(blockProcessor);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("blockProcessorProvider")
  void testProcessSlotReadThenUpdateTx(
      final String ignoredName, final BlockProcessor blockProcessor) {
    processSlotReadThenUpdateTx(blockProcessor);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("blockProcessorProvider")
  void testProcessAccountReadThenUpdateTx(
      final String ignoredName, final BlockProcessor blockProcessor) {
    processAccountReadThenUpdateTx(blockProcessor);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("blockProcessorProvider")
  void testProcessAccountUpdateThenReadTx(
      final String ignoredName, final BlockProcessor blockProcessor) {
    processAccountUpdateThenReadTx(blockProcessor);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("blockProcessorProvider")
  void testProcessAccountReadThenUpdateTxWithTwoAccounts(
      final String ignoredName, final BlockProcessor blockProcessor) {
    processAccountReadThenUpdateTxWithTwoAccounts(blockProcessor);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("blockProcessorProvider")
  void testProcessAccountUpdateThenReadTeTxWithTwoAccounts(
      final String ignoredName, final BlockProcessor blockProcessor) {
    processAccountUpdateThenReadTxWithTwoAccounts(blockProcessor);
  }

  private void processSimpleTransfers(final BlockProcessor blockProcessor) {
    // Create two non conflicted transactions
    Transaction transactionTransfer1 = // ACCOUNT_GENESIS_1 -> ACCOUNT_2
        createTransferTransaction(
            0, 1_000_000_000_000_000_000L, 300000L, 5L, 7L, ACCOUNT_2, ACCOUNT_GENESIS_1_KEYPAIR);
    Transaction transactionTransfer2 = // ACCOUNT_GENESIS_2 -> ACCOUNT_3
        createTransferTransaction(
            0, 2_000_000_000_000_000_000L, 300000L, 5L, 7L, ACCOUNT_3, ACCOUNT_GENESIS_2_KEYPAIR);

    MutableWorldState worldState = worldStateArchive.getMutable();
    BonsaiAccount senderAccount1 = (BonsaiAccount) worldState.get(transactionTransfer1.getSender());
    BonsaiAccount senderAccount2 = (BonsaiAccount) worldState.get(transactionTransfer2.getSender());

    Block blockWithTransactions =
        createBlockWithTransactions(
            "0x4ca6e755674a1df696e5365361a0c352422934ba3ad0a74c9e6b0b56e4f80b4c",
            transactionTransfer1,
            transactionTransfer2);

    BlockProcessingResult blockProcessingResult =
        blockProcessor.processBlock(blockchain, worldState, blockWithTransactions);

    BonsaiAccount updatedSenderAccount1 =
        (BonsaiAccount) worldState.get(transactionTransfer1.getSender());
    BonsaiAccount updatedSenderAccount2 =
        (BonsaiAccount) worldState.get(transactionTransfer2.getSender());

    BonsaiAccount updatedAccount0x2 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_2));
    BonsaiAccount updatedAccount0x3 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_3));

    assertTrue(blockProcessingResult.isSuccessful());
    assertThat(updatedAccount0x2.getBalance()).isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(updatedAccount0x3.getBalance()).isEqualTo(Wei.of(2_000_000_000_000_000_000L));
    assertThat(updatedSenderAccount1.getBalance()).isLessThan(senderAccount1.getBalance());
    assertThat(updatedSenderAccount2.getBalance()).isLessThan(senderAccount2.getBalance());
  }

  private void processConflictedSimpleTransfersSameSender(final BlockProcessor blockProcessor) {
    // Create three transactions with the same sender
    Transaction transferTransaction1 = // ACCOUNT_GENESIS_1 -> ACCOUNT_4
        createTransferTransaction(
            0, 1_000_000_000_000_000_000L, 300000L, 5L, 7L, ACCOUNT_4, ACCOUNT_GENESIS_1_KEYPAIR);
    Transaction transferTransaction2 = // ACCOUNT_GENESIS_1 -> ACCOUNT_5
        createTransferTransaction(
            1, 2_000_000_000_000_000_000L, 300000L, 5L, 7L, ACCOUNT_5, ACCOUNT_GENESIS_1_KEYPAIR);
    Transaction transferTransaction3 = // ACCOUNT_GENESIS_1 -> ACCOUNT_6
        createTransferTransaction(
            2, 3_000_000_000_000_000_000L, 300000L, 5L, 7L, ACCOUNT_6, ACCOUNT_GENESIS_1_KEYPAIR);

    MutableWorldState worldState = worldStateArchive.getMutable();
    BonsaiAccount senderAccount = (BonsaiAccount) worldState.get(transferTransaction1.getSender());

    Block blockWithTransactions =
        createBlockWithTransactions(
            "0x7420935ee980cb06060f119ee3ee3dcd5a96989985938a3b3ca096558ad61484",
            transferTransaction1,
            transferTransaction2,
            transferTransaction3);

    BlockProcessingResult blockProcessingResult =
        blockProcessor.processBlock(blockchain, worldState, blockWithTransactions);

    BonsaiAccount updatedSenderAccount =
        (BonsaiAccount) worldState.get(transferTransaction1.getSender());

    BonsaiAccount updatedAccount0x4 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_4));
    BonsaiAccount updatedAccount0x5 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_5));
    BonsaiAccount updatedAccount0x6 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_6));

    assertTrue(blockProcessingResult.isSuccessful());
    assertThat(updatedAccount0x4.getBalance()).isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(updatedAccount0x5.getBalance()).isEqualTo(Wei.of(2_000_000_000_000_000_000L));
    assertThat(updatedAccount0x6.getBalance()).isEqualTo(Wei.of(3_000_000_000_000_000_000L));

    assertThat(updatedSenderAccount.getBalance()).isLessThan(senderAccount.getBalance());
  }

  private void processConflictedSimpleTransfersSameAddressReceiverAndSender(
      final BlockProcessor blockProcessor) {
    // Create conflicted transfer transactions
    Transaction transferTransaction1 =
        createTransferTransaction(
            0,
            1_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            ACCOUNT_GENESIS_2,
            ACCOUNT_GENESIS_1_KEYPAIR); // ACCOUNT_GENESIS_1 -> ACCOUNT_GENESIS_2
    Transaction transferTransaction2 =
        createTransferTransaction(
            0,
            2_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            ACCOUNT_2,
            ACCOUNT_GENESIS_2_KEYPAIR); // ACCOUNT_GENESIS_2 -> ACCOUNT_2

    MutableWorldState worldState = worldStateArchive.getMutable();
    BonsaiAccount transferTransaction1Sender =
        (BonsaiAccount) worldState.get(transferTransaction1.getSender());

    Block blockWithTransactions =
        createBlockWithTransactions(
            "0x5c0158e79b66c86cf5e5256390b95add0c2e6891c24e72d71b9dbea5845fea72",
            transferTransaction1,
            transferTransaction2);

    BlockProcessingResult blockProcessingResult =
        blockProcessor.processBlock(blockchain, worldState, blockWithTransactions);

    BonsaiAccount updatedSenderAccount1 =
        (BonsaiAccount) worldState.get(transferTransaction1.getSender());
    BonsaiAccount updatedGenesisAccount1 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_GENESIS_1));
    BonsaiAccount updatedGenesisAccount2 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_GENESIS_2));
    BonsaiAccount updatedAccount0x2 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_2));

    assertTrue(blockProcessingResult.isSuccessful());
    assertThat(updatedGenesisAccount1.getBalance())
        .isEqualTo(
            Wei.of(
                UInt256.fromHexString(
                    ("0x00000000000000000000000000000000000000000000003627e8f7123739c1c8"))));
    assertThat(updatedGenesisAccount2.getBalance())
        .isEqualTo(
            Wei.of(
                UInt256.fromHexString(
                    ("0x00000000000000000000000000000000000000000000003627e8f7123739c024"))));
    assertThat(updatedAccount0x2.getBalance()).isEqualTo(Wei.of(2_000_000_000_000_000_000L));
    assertThat(updatedSenderAccount1.getBalance())
        .isLessThan(transferTransaction1Sender.getBalance());
  }

  private void processConflictedSimpleTransfersWithCoinbase(final BlockProcessor blockProcessor) {
    // Create conflicted transactions using coinbase
    Transaction transferTransaction1 =
        createTransferTransaction(
            0,
            1_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            ACCOUNT_2,
            ACCOUNT_GENESIS_1_KEYPAIR); // ACCOUNT_GENESIS_1 -> ACCOUNT_2
    Transaction transferTransaction2 =
        createTransferTransaction(
            0,
            2_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            coinbase.toHexString(),
            ACCOUNT_GENESIS_2_KEYPAIR); // ACCOUNT_GENESIS_2 -> COINBASE

    MutableWorldState worldState = worldStateArchive.getMutable();
    BonsaiAccount transferTransaction1Sender =
        (BonsaiAccount) worldState.get(transferTransaction1.getSender());
    Block blockWithTransactions =
        createBlockWithTransactions(
            "0xd9544f389692face27352d23494dd1446d9af025067bc11b29e0eb83e258676a",
            transferTransaction1,
            transferTransaction2);

    BlockProcessingResult blockProcessingResult =
        blockProcessor.processBlock(blockchain, worldState, blockWithTransactions);

    BonsaiAccount updatedSenderAccount1 =
        (BonsaiAccount) worldState.get(transferTransaction1.getSender());

    BonsaiAccount updatedAccount0x2 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_2));

    BonsaiAccount updatedCoinbase = (BonsaiAccount) worldState.get(coinbase);

    assertTrue(blockProcessingResult.isSuccessful());
    assertThat(updatedAccount0x2.getBalance()).isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(updatedCoinbase.getBalance())
        .isEqualTo(
            Wei.of(
                UInt256.fromHexString(
                    ("0x0000000000000000000000000000000000000000000000001bc8886498566008"))));

    assertThat(updatedSenderAccount1.getBalance())
        .isLessThan(transferTransaction1Sender.getBalance());
  }

  void processContractSlotUpdateThenReadTx(final BlockProcessor blockProcessor) {
    Address contractAddress = Address.fromHexStringStrict(CONTRACT_ADDRESS);

    // create conflicted transactions on the same slot (update then read)
    Transaction setSlot1Transaction =
        createContractUpdateSlotTransaction(
            0, contractAddress, "setSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(100));
    Transaction getSlot1Transaction =
        createContractUpdateSlotTransaction(
            0, contractAddress, "getSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.empty());
    Transaction setSlot3Transaction =
        createContractUpdateSlotTransaction(
            1, contractAddress, "setSlot2", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(200));
    Transaction setSlot4Transaction =
        createContractUpdateSlotTransaction(
            2, contractAddress, "setSlot3", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(300));

    Block blockWithTransactions =
        createBlockWithTransactions(
            "0x51d59f64426ea986b1323aa22b9881c83f67947b4f90c2c302b21d3f8c459aff",
            setSlot1Transaction,
            getSlot1Transaction,
            setSlot3Transaction,
            setSlot4Transaction);

    MutableWorldState worldState = worldStateArchive.getMutable();
    BlockProcessingResult blockProcessingResult =
        blockProcessor.processBlock(blockchain, worldState, blockWithTransactions);

    assertTrue(blockProcessingResult.isSuccessful());

    // Verify the state
    assertContractStorage(worldState, contractAddress, 0, 100);
    assertContractStorage(worldState, contractAddress, 1, 200);
    assertContractStorage(worldState, contractAddress, 2, 300);
  }

  void processSlotReadThenUpdateTx(final BlockProcessor blockProcessor) {
    Address contractAddress = Address.fromHexStringStrict(CONTRACT_ADDRESS);

    Transaction getSlot1Transaction =
        createContractUpdateSlotTransaction(
            0, contractAddress, "getSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.empty());
    Transaction setSlot1Transaction =
        createContractUpdateSlotTransaction(
            0, contractAddress, "setSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.of(1000));
    Transaction setSlo2Transaction =
        createContractUpdateSlotTransaction(
            1, contractAddress, "setSlot2", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(2000));
    Transaction setSlot3Transaction =
        createContractUpdateSlotTransaction(
            2, contractAddress, "setSlot3", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(3000));

    Block blockWithTransactions =
        createBlockWithTransactions(
            "0xdf21d4fef211d7a905022dc87f2a68f4bf9cb273fcf9745cfa7f7c2f258c03f3",
            getSlot1Transaction,
            setSlot1Transaction,
            setSlo2Transaction,
            setSlot3Transaction);
    MutableWorldState worldState = worldStateArchive.getMutable();

    BlockProcessingResult blockProcessingResult =
        blockProcessor.processBlock(blockchain, worldState, blockWithTransactions);

    assertTrue(blockProcessingResult.isSuccessful());

    // Verify the state
    assertContractStorage(worldState, contractAddress, 0, 1000);
    assertContractStorage(worldState, contractAddress, 1, 2000);
    assertContractStorage(worldState, contractAddress, 2, 3000);
  }

  void processAccountReadThenUpdateTx(final BlockProcessor blockProcessor) {
    Address contractAddress = Address.fromHexStringStrict(CONTRACT_ADDRESS);
    Transaction transactionTransfer = // ACCOUNT_GENESIS_1 -> CONTRACT_ADDRESS
        createTransferTransaction(
            0,
            1_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            CONTRACT_ADDRESS,
            ACCOUNT_GENESIS_1_KEYPAIR);
    Transaction getcontractBalanceTransaction =
        createContractReadAccountTransaction(
            1, contractAddress, "getBalance", ACCOUNT_GENESIS_1_KEYPAIR, ACCOUNT_2);

    Transaction sendEthFromContractTransaction =
        createContractUpdateAccountTransaction(
            0,
            contractAddress,
            "transferTo",
            ACCOUNT_GENESIS_2_KEYPAIR,
            ACCOUNT_2,
            500_000_000_000_000_000L);

    Block blockWithTransactions =
        createBlockWithTransactions(
            "0x91966cdde619acb05a1d9fef2f8801432a30edde7131f1f194002b0a766026c7",
            transactionTransfer,
            getcontractBalanceTransaction,
            sendEthFromContractTransaction);
    MutableWorldState worldState = worldStateArchive.getMutable();

    BlockProcessingResult blockProcessingResult =
        blockProcessor.processBlock(blockchain, worldState, blockWithTransactions);

    assertTrue(blockProcessingResult.isSuccessful());

    // Verify the state
    BonsaiAccount contractAccount = (BonsaiAccount) worldState.get(contractAddress);
    BonsaiAccount updatedAccount0x2 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_2));
    assertThat(contractAccount.getBalance()).isEqualTo(Wei.of(500_000_000_000_000_000L));
    assertThat(updatedAccount0x2.getBalance()).isEqualTo(Wei.of(500_000_000_000_000_000L));
  }

  void processAccountUpdateThenReadTx(final BlockProcessor blockProcessor) {
    Address contractAddress = Address.fromHexStringStrict(CONTRACT_ADDRESS);
    Transaction transactionTransfer =
        createTransferTransaction(
            0,
            1_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            CONTRACT_ADDRESS,
            ACCOUNT_GENESIS_1_KEYPAIR);

    Transaction sendEthFromContractTransaction =
        createContractUpdateAccountTransaction(
            1,
            contractAddress,
            "transferTo",
            ACCOUNT_GENESIS_1_KEYPAIR,
            ACCOUNT_2,
            500_000_000_000_000_000L);

    Transaction getcontractBalanceTransaction =
        createContractReadAccountTransaction(
            0, contractAddress, "getBalance", ACCOUNT_GENESIS_2_KEYPAIR, ACCOUNT_2);

    Block blockWithTransactions =
        createBlockWithTransactions(
            "0x375af730c0f9e04666659fc419fda74cc0cb29936607c08adf21d3b236c6b7f6",
            transactionTransfer,
            sendEthFromContractTransaction,
            getcontractBalanceTransaction);
    MutableWorldState worldState = worldStateArchive.getMutable();

    BlockProcessingResult blockProcessingResult =
        blockProcessor.processBlock(blockchain, worldState, blockWithTransactions);
    assertTrue(blockProcessingResult.isSuccessful());

    // Verify the state
    BonsaiAccount contractAccount = (BonsaiAccount) worldState.get(contractAddress);
    BonsaiAccount updatedAccount0x2 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_2));

    assertThat(contractAccount.getBalance()).isEqualTo(Wei.of(500_000_000_000_000_000L));
    assertThat(updatedAccount0x2.getBalance()).isEqualTo(Wei.of(500_000_000_000_000_000L));
  }

  void processAccountReadThenUpdateTxWithTwoAccounts(final BlockProcessor blockProcessor) {
    Address contractAddress = Address.fromHexStringStrict(CONTRACT_ADDRESS);
    Transaction transactionTransfer = // ACCOUNT_GENESIS_1 -> CONTRACT_ADDRESS
        createTransferTransaction(
            0,
            1_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            CONTRACT_ADDRESS,
            ACCOUNT_GENESIS_1_KEYPAIR);
    Transaction getcontractBalanceTransaction =
        createContractReadAccountTransaction(
            1, contractAddress, "getBalance", ACCOUNT_GENESIS_1_KEYPAIR, ACCOUNT_2);

    Transaction sendEthFromContractTransaction =
        createContractUpdateAccountTransaction(
            0,
            contractAddress,
            "transferTo",
            ACCOUNT_GENESIS_2_KEYPAIR,
            ACCOUNT_3,
            500_000_000_000_000_000L);

    Block blockWithTransactions =
        createBlockWithTransactions(
            "0x3c2366a28dadbcef39ba04cde7bc30a5dccfce1e478a5c2602f5a28ab9498e6c",
            transactionTransfer,
            getcontractBalanceTransaction,
            sendEthFromContractTransaction);
    MutableWorldState worldState = worldStateArchive.getMutable();

    BlockProcessingResult blockProcessingResult =
        blockProcessor.processBlock(blockchain, worldState, blockWithTransactions);

    assertTrue(blockProcessingResult.isSuccessful());

    // Verify the state
    BonsaiAccount contractAccount = (BonsaiAccount) worldState.get(contractAddress);
    BonsaiAccount updatedAccount0x3 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_3));
    assertThat(contractAccount.getBalance()).isEqualTo(Wei.of(500_000_000_000_000_000L));
    assertThat(updatedAccount0x3.getBalance()).isEqualTo(Wei.of(500_000_000_000_000_000L));
  }

  void processAccountUpdateThenReadTxWithTwoAccounts(final BlockProcessor blockProcessor) {
    Address contractAddress = Address.fromHexStringStrict(CONTRACT_ADDRESS);
    Transaction transactionTransfer = // ACCOUNT_GENESIS_1 -> CONTRACT_ADDRESS
        createTransferTransaction(
            0,
            1_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            CONTRACT_ADDRESS,
            ACCOUNT_GENESIS_1_KEYPAIR);

    Transaction sendEthFromContractTransaction =
        createContractUpdateAccountTransaction(
            0,
            contractAddress,
            "transferTo",
            ACCOUNT_GENESIS_2_KEYPAIR,
            ACCOUNT_3,
            500_000_000_000_000_000L);

    Transaction getcontractBalanceTransaction =
        createContractReadAccountTransaction(
            1, contractAddress, "getBalance", ACCOUNT_GENESIS_1_KEYPAIR, ACCOUNT_2);

    Block blockWithTransactions =
        createBlockWithTransactions(
            "0x3c2366a28dadbcef39ba04cde7bc30a5dccfce1e478a5c2602f5a28ab9498e6c",
            transactionTransfer,
            sendEthFromContractTransaction,
            getcontractBalanceTransaction);
    MutableWorldState worldState = worldStateArchive.getMutable();

    BlockProcessingResult blockProcessingResult =
        blockProcessor.processBlock(blockchain, worldState, blockWithTransactions);

    assertTrue(blockProcessingResult.isSuccessful());

    // Verify the state
    BonsaiAccount contractAccount = (BonsaiAccount) worldState.get(contractAddress);
    BonsaiAccount updatedAccount0x3 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_3));
    assertThat(contractAccount.getBalance()).isEqualTo(Wei.of(500_000_000_000_000_000L));
    assertThat(updatedAccount0x3.getBalance()).isEqualTo(Wei.of(500_000_000_000_000_000L));
  }

  private static KeyPair generateKeyPair(final String privateKeyHex) {
    final KeyPair keyPair =
        SignatureAlgorithmFactory.getInstance()
            .createKeyPair(
                SECPPrivateKey.create(
                    Bytes32.fromHexString(privateKeyHex), SignatureAlgorithm.ALGORITHM));
    return keyPair;
  }

  private Transaction createContractUpdateSlotTransaction(
      final int nonce,
      final Address contractAddress,
      final String methodSignature,
      final KeyPair keyPair,
      final Optional<Integer> value) {
    Bytes payload = encodeFunctionCall(methodSignature, value);
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .nonce(nonce)
        .maxPriorityFeePerGas(Wei.of(5))
        .maxFeePerGas(Wei.of(7))
        .gasLimit(3000000L)
        .to(contractAddress)
        .value(Wei.ZERO)
        .payload(payload)
        .chainId(BigInteger.valueOf(42))
        .signAndBuild(keyPair);
  }

  private Transaction createContractReadAccountTransaction(
      final int nonce,
      final Address contractAddress,
      final String methodSignature,
      final KeyPair keyPair,
      final String address) {
    Bytes payload = encodeFunctionCall(methodSignature, address);
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .nonce(nonce)
        .maxPriorityFeePerGas(Wei.of(5))
        .maxFeePerGas(Wei.of(7))
        .gasLimit(3000000L)
        .to(contractAddress)
        .value(Wei.ZERO)
        .payload(payload)
        .chainId(BigInteger.valueOf(42))
        .signAndBuild(keyPair);
  }

  private Transaction createContractUpdateAccountTransaction(
      final int nonce,
      final Address contractAddress,
      final String methodSignature,
      final KeyPair keyPair,
      final String address,
      final long value) {
    Bytes payload = encodeFunctionCall(methodSignature, address, value);
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .nonce(nonce)
        .maxPriorityFeePerGas(Wei.of(5))
        .maxFeePerGas(Wei.of(7))
        .gasLimit(3000000L)
        .to(contractAddress)
        .value(Wei.ZERO)
        .payload(payload)
        .chainId(BigInteger.valueOf(42))
        .signAndBuild(keyPair);
  }

  private Bytes encodeFunctionCall(final String methodSignature, final Optional<Integer> value) {
    List<Type> inputParameters =
        value.isPresent() ? Arrays.<Type>asList(new Uint256(value.get())) : List.of();
    Function function = new Function(methodSignature, inputParameters, List.of());
    return Bytes.fromHexString(FunctionEncoder.encode(function));
  }

  private Bytes encodeFunctionCall(final String methodSignature, final String address) {
    List<Type> inputParameters = Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(address));
    Function function = new Function(methodSignature, inputParameters, List.of());
    return Bytes.fromHexString(FunctionEncoder.encode(function));
  }

  private Bytes encodeFunctionCall(
      final String methodSignature, final String address, final long value) {
    List<Type> inputParameters =
        Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(address), new Uint256(value));
    Function function = new Function(methodSignature, inputParameters, List.of());
    return Bytes.fromHexString(FunctionEncoder.encode(function));
  }

  private Block createBlockWithTransactions(
      final String stateRoot, final Transaction... transactions) {
    BlockHeader blockHeader =
        new BlockHeaderTestFixture()
            .number(1L)
            .stateRoot(Hash.fromHexString(stateRoot))
            .gasLimit(30_000_000L)
            .baseFeePerGas(Wei.of(5))
            .buildHeader();
    BlockBody blockBody = new BlockBody(Arrays.asList(transactions), Collections.emptyList());
    return new Block(blockHeader, blockBody);
  }

  private void assertContractStorage(
      final MutableWorldState worldState,
      final Address contractAddress,
      final int slot,
      final int expectedValue) {
    BonsaiAccount contractAccount = (BonsaiAccount) worldState.get(contractAddress);
    UInt256 actualValue = contractAccount.getStorageValue(UInt256.valueOf(slot));
    assertThat(actualValue).isEqualTo(UInt256.valueOf(expectedValue));
  }

  private Transaction createTransferTransaction(
      final long nonce,
      final long value,
      final long gasLimit,
      final long maxPriorityFeePerGas,
      final long maxFeePerGas,
      final String hexAddress,
      final KeyPair keyPair) {
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .nonce(nonce)
        .maxPriorityFeePerGas(Wei.of(maxPriorityFeePerGas))
        .maxFeePerGas(Wei.of(maxFeePerGas))
        .gasLimit(gasLimit)
        .to(Address.fromHexStringStrict(hexAddress))
        .value(Wei.of(value))
        .payload(Bytes.EMPTY)
        .chainId(BigInteger.valueOf(42))
        .signAndBuild(keyPair);
  }
}
