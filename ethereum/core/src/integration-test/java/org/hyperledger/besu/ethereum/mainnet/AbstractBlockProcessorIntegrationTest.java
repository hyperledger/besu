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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractBlockProcessorIntegrationTest {

  private static final String ACCOUNT_GENESIS_1 = "627306090abab3a6e1400e9345bc60c78a8bef57";
  private static final String ACCOUNT_GENESIS_2 = "7f2d653f56ea8de6ffa554c7a0cd4e03af79f3eb";

  private static final String ACCOUNT_2 = "0000000000000000000000000000000000000002";
  private static final String ACCOUNT_3 = "0000000000000000000000000000000000000003";
  private static final String ACCOUNT_4 = "0000000000000000000000000000000000000004";
  private static final String ACCOUNT_5 = "0000000000000000000000000000000000000005";
  private static final String ACCOUNT_6 = "0000000000000000000000000000000000000006";

  private WorldStateArchive worldStateArchive;
  private MainnetParallelBlockProcessor parallelBlockProcessor;
  private BlockProcessor blockProcessor;
  private DefaultBlockchain blockchain;
  private Address coinbase;

  @BeforeEach
  public void setUp() {
    final ExecutionContextTestFixture contextTestFixture =
        ExecutionContextTestFixture.builder(GenesisConfigFile.fromResource("/genesis-bp-it.json"))
            .build();
    final ProtocolSchedule protocolSchedule = contextTestFixture.getProtocolSchedule();
    final BlockHeader blockHeader = new BlockHeaderTestFixture().number(0L).buildHeader();
    final MainnetTransactionProcessor transactionProcessor =
        protocolSchedule.getByBlockHeader(blockHeader).getTransactionProcessor();
    coinbase = blockHeader.getCoinbase();
    worldStateArchive = contextTestFixture.getStateArchive();
    blockchain = (DefaultBlockchain) contextTestFixture.getBlockchain();
    blockProcessor =
        new MainnetBlockProcessor(
            transactionProcessor,
            protocolSchedule
                .getByBlockHeader(new BlockHeaderTestFixture().number(0L).buildHeader())
                .getTransactionReceiptFactory(),
            Wei.of(2_000_000_000_000_000L), // Example block reward
            BlockHeader::getCoinbase,
            false,
            protocolSchedule);
    parallelBlockProcessor =
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
  }

  @Test
  void testSequentialBlockProcessingwithTransfers() {
    processSimpleTransfers(blockProcessor);
  }

  @Test
  void testParallelBlockProcessingWithTransfers() {
    processSimpleTransfers(parallelBlockProcessor);
  }

  @Test
  void testSequentiaConfiltedTransfers1() {
    processConfiltedSimpleTransfers1(blockProcessor);
  }

  @Test
  void testParallelConfiltedTransfers1() {
    processConfiltedSimpleTransfers1(parallelBlockProcessor);
  }

  @Test
  void testSequentiaConfiltedTransfers2() {
    processConfiltedSimpleTransfers2(blockProcessor);
  }

  @Test
  void testParallelConfiltedTransfers2() {
    processConfiltedSimpleTransfers2(parallelBlockProcessor);
  }

  @Test
  void processSequentialConfiltedSimpleTransfersWithCoinbase() {
    processConfiltedSimpleTransfersWithCoinbase(blockProcessor);
  }

  @Test
  void processParallelConfiltedSimpleTransfersWithCoinbase() {
    processConfiltedSimpleTransfersWithCoinbase(parallelBlockProcessor);
  }

  private void processSimpleTransfers(final BlockProcessor blockProcessor) {
    final KeyPair keyPair1 =
        SignatureAlgorithmFactory.getInstance()
            .createKeyPair(
                SECPPrivateKey.create(
                    Bytes32.fromHexString(
                        "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3"),
                    SignatureAlgorithm.ALGORITHM));
    final KeyPair keyPair2 =
        SignatureAlgorithmFactory.getInstance()
            .createKeyPair(
                SECPPrivateKey.create(
                    Bytes32.fromHexString(
                        "fc5141e75bf622179f8eedada7fab3e2e6b3e3da8eb9df4f46d84df22df7430e"),
                    SignatureAlgorithm.ALGORITHM));

    // Create two non conflicted transactions
    Transaction transaction1 =
        createTransferTransaction(
            0, 1_000_000_000_000_000_000L, 300000L, 5L, 7L, ACCOUNT_2, keyPair1);
    Transaction transaction2 =
        createTransferTransaction(
            0, 2_000_000_000_000_000_000L, 300000L, 5L, 7L, ACCOUNT_3, keyPair2);

    MutableWorldState worldState = worldStateArchive.getMutable();
    BonsaiAccount senderAccount1 = (BonsaiAccount) worldState.get(transaction1.getSender());
    BonsaiAccount senderAccount2 = (BonsaiAccount) worldState.get(transaction1.getSender());

    BlockHeader blockHeader =
        new BlockHeaderTestFixture()
            .number(1L)
            .stateRoot(
                Hash.fromHexString(
                    "0xccfe44747301aded612373d9f2281131a45a53422665babe571e0c03465a979b"))
            .gasLimit(30_000_000L)
            .baseFeePerGas(Wei.of(5))
            .buildHeader();
    BlockBody blockBody =
        new BlockBody(Arrays.asList(transaction1, transaction2), Collections.emptyList());
    BlockProcessingResult result =
        blockProcessor.processBlock(
            blockchain, worldStateArchive.getMutable(), new Block(blockHeader, blockBody));

    BonsaiAccount updatedSenderAccount1 = (BonsaiAccount) worldState.get(transaction1.getSender());
    BonsaiAccount updatedSenderAccount2 = (BonsaiAccount) worldState.get(transaction2.getSender());

    BonsaiAccount updatedAccount0x1 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_2));
    BonsaiAccount updatedAccount0x2 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_3));

    assertTrue(result.isSuccessful());
    assertThat(updatedAccount0x1.getBalance()).isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(updatedAccount0x2.getBalance()).isEqualTo(Wei.of(2_000_000_000_000_000_000L));
    assertThat(updatedSenderAccount1.getBalance()).isLessThan(senderAccount1.getBalance());
    assertThat(updatedSenderAccount2.getBalance()).isLessThan(senderAccount2.getBalance());
  }

  private void processConfiltedSimpleTransfers1(final BlockProcessor blockProcessor) {
    final KeyPair keyPair =
        generateKeyPair("c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3");

    // Create three transactions with the same sender
    Transaction transaction1 =
        createTransferTransaction(
            0, 1_000_000_000_000_000_000L, 300000L, 5L, 7L, ACCOUNT_4, keyPair);
    Transaction transaction2 =
        createTransferTransaction(
            1, 2_000_000_000_000_000_000L, 300000L, 5L, 7L, ACCOUNT_5, keyPair);
    Transaction transaction3 =
        createTransferTransaction(
            2, 3_000_000_000_000_000_000L, 300000L, 5L, 7L, ACCOUNT_6, keyPair);

    MutableWorldState worldState = worldStateArchive.getMutable();
    BonsaiAccount account = (BonsaiAccount) worldState.get(transaction1.getSender());

    BlockHeader blockHeader =
        new BlockHeaderTestFixture()
            .number(1L)
            .stateRoot(
                Hash.fromHexString(
                    "0x6caed47324445abe7575c034b02b235ce1842259b4bf616aebb329ca9b89db38"))
            .gasLimit(30_000_000L)
            .baseFeePerGas(Wei.of(5))
            .buildHeader();
    BlockBody blockBody =
        new BlockBody(
            Arrays.asList(transaction1, transaction2, transaction3), Collections.emptyList());
    BlockProcessingResult result =
        blockProcessor.processBlock(
            blockchain, worldStateArchive.getMutable(), new Block(blockHeader, blockBody));

    BonsaiAccount updatedAccount = (BonsaiAccount) worldState.get(transaction1.getSender());

    BonsaiAccount updatedAccount0x1 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_4));
    BonsaiAccount updatedAccount0x2 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_5));
    BonsaiAccount updatedAccount0x3 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_6));

    assertTrue(result.isSuccessful());
    assertThat(updatedAccount0x1.getBalance()).isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(updatedAccount0x2.getBalance()).isEqualTo(Wei.of(2_000_000_000_000_000_000L));
    assertThat(updatedAccount0x3.getBalance()).isEqualTo(Wei.of(3_000_000_000_000_000_000L));

    assertThat(updatedAccount.getBalance()).isLessThan(account.getBalance());
  }

  private static KeyPair generateKeyPair(final String privateKeyHex) {
    final KeyPair keyPair =
        SignatureAlgorithmFactory.getInstance()
            .createKeyPair(
                SECPPrivateKey.create(
                    Bytes32.fromHexString(privateKeyHex), SignatureAlgorithm.ALGORITHM));
    return keyPair;
  }

  private void processConfiltedSimpleTransfers2(final BlockProcessor blockProcessor) {
    final KeyPair keyPair =
        generateKeyPair("c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3");
    final KeyPair keyPair2 =
        generateKeyPair("fc5141e75bf622179f8eedada7fab3e2e6b3e3da8eb9df4f46d84df22df7430e");

    // Create three transactions with the same sender (conflicted transactions)
    Transaction transaction1 =
        createTransferTransaction(
            0,
            1_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            ACCOUNT_GENESIS_2,
            keyPair); // ACCOUNT_GENESIS_1 -> ACCOUNT_GENESIS_2
    Transaction transaction2 =
        createTransferTransaction(
            0,
            2_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            ACCOUNT_2,
            keyPair2); // ACCOUNT_GENESIS_2 -> ACCOUNT_@

    MutableWorldState worldState = worldStateArchive.getMutable();
    BonsaiAccount account = (BonsaiAccount) worldState.get(transaction1.getSender());

    BlockHeader blockHeader =
        new BlockHeaderTestFixture()
            .number(1L)
            .stateRoot(
                Hash.fromHexString(
                    "0x1a44fb9bac7171357696afa8e2861a3ea7f66bd43452ff2aa8eae5e31d2b8e8f"))
            .gasLimit(30_000_000L)
            .baseFeePerGas(Wei.of(5))
            .buildHeader();
    BlockBody blockBody =
        new BlockBody(Arrays.asList(transaction1, transaction2), Collections.emptyList());
    BlockProcessingResult result =
        blockProcessor.processBlock(
            blockchain, worldStateArchive.getMutable(), new Block(blockHeader, blockBody));

    BonsaiAccount updatedAccount = (BonsaiAccount) worldState.get(transaction1.getSender());

    BonsaiAccount updatedGenesisAccount1 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_GENESIS_1));
    BonsaiAccount updatedGenesisAccount2 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_GENESIS_2));
    BonsaiAccount updatedAccount0x2 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_2));

    assertTrue(result.isSuccessful());
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

    assertThat(updatedAccount.getBalance()).isLessThan(account.getBalance());
  }

  private void processConfiltedSimpleTransfersWithCoinbase(final BlockProcessor blockProcessor) {
    final KeyPair keyPair =
        generateKeyPair("c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3");
    final KeyPair keyPair2 =
        generateKeyPair("fc5141e75bf622179f8eedada7fab3e2e6b3e3da8eb9df4f46d84df22df7430e");

    // Create three transactions with the same sender (conflicted transactions)
    Transaction transaction1 =
        createTransferTransaction(
            0,
            1_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            ACCOUNT_2,
            keyPair); // ACCOUNT_GENESIS_1 -> ACCOUNT_GENESIS_2
    Transaction transaction2 =
        createTransferTransaction(
            0,
            2_000_000_000_000_000_000L,
            300000L,
            5L,
            7L,
            coinbase.toHexString(),
            keyPair2); // ACCOUNT_GENESIS_2 -> ACCOUNT_@

    MutableWorldState worldState = worldStateArchive.getMutable();
    BonsaiAccount account = (BonsaiAccount) worldState.get(transaction1.getSender());

    BlockHeader blockHeader =
        new BlockHeaderTestFixture()
            .number(1L)
            .stateRoot(
                Hash.fromHexString(
                    "0x94e99fa3c5e8c415fd76a52943d405844255dbc610f75e1b4fdc10211c0dd817"))
            .gasLimit(30_000_000L)
            .baseFeePerGas(Wei.of(5))
            .buildHeader();
    BlockBody blockBody =
        new BlockBody(Arrays.asList(transaction1, transaction2), Collections.emptyList());
    BlockProcessingResult result =
        blockProcessor.processBlock(
            blockchain, worldStateArchive.getMutable(), new Block(blockHeader, blockBody));

    BonsaiAccount updatedAccount = (BonsaiAccount) worldState.get(transaction1.getSender());

    BonsaiAccount updatedAccount0x2 =
        (BonsaiAccount) worldState.get(Address.fromHexStringStrict(ACCOUNT_2));

    BonsaiAccount updatedCoinbase = (BonsaiAccount) worldState.get(coinbase);

    assertTrue(result.isSuccessful());
    assertThat(updatedAccount0x2.getBalance()).isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(updatedCoinbase.getBalance())
        .isEqualTo(
            Wei.of(
                UInt256.fromHexString(
                    ("0x0000000000000000000000000000000000000000000000001bc8886498566008"))));

    assertThat(updatedAccount.getBalance()).isLessThan(account.getBalance());
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
