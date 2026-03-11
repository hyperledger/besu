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
package org.hyperledger.besu.ethereum.mainnet.parallelization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.BonsaiAccount;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

/**
 * Abstract integration tests for parallel block processing. Subclasses provide specific parallel
 * preprocessing implementations (BAL or Optimistic) to verify that both produce identical state
 * roots to sequential execution across various conflict scenarios.
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractParallelBlockProcessorIntegrationTest {

  protected static final String GENESIS_CONFIG =
      "/org/hyperledger/besu/ethereum/mainnet/parallelization/genesis-it.json";

  protected static final Address MINING_BENEFICIARY =
      Address.fromHexStringStrict("0xa4664C40AACeBD82A2Db79f0ea36C06Bc6A19Adb");

  protected static final String ACCOUNT_GENESIS_1 = "0x627306090abab3a6e1400e9345bc60c78a8bef57";
  protected static final String ACCOUNT_GENESIS_2 = "0x7f2d653f56ea8de6ffa554c7a0cd4e03af79f3eb";
  protected static final String ACCOUNT_2 = "0x0000000000000000000000000000000000000002";
  protected static final String ACCOUNT_3 = "0x0000000000000000000000000000000000000003";
  protected static final String ACCOUNT_4 = "0x0000000000000000000000000000000000000004";
  protected static final String ACCOUNT_5 = "0x0000000000000000000000000000000000000005";
  protected static final String ACCOUNT_6 = "0x0000000000000000000000000000000000000006";
  protected static final String CONTRACT_ADDRESS = "0x00000000000000000000000000000000000fffff";
  protected static final String PARALLEL_TEST_CONTRACT =
      "0x00000000000000000000000000000000000eeeee";

  protected static final KeyPair ACCOUNT_GENESIS_1_KEYPAIR =
      generateKeyPair("c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3");
  protected static final KeyPair ACCOUNT_GENESIS_2_KEYPAIR =
      generateKeyPair("fc5141e75bf622179f8eedada7fab3e2e6b3e3da8eb9df4f46d84df22df7430e");

  protected static final BalConfiguration SEQUENTIAL_CONFIG = BalConfiguration.DEFAULT;

  protected abstract String getVariantName();

  protected abstract ParallelTransactionPreprocessing createParallelPreprocessing(
      MainnetTransactionProcessor transactionProcessor);

  protected BalConfiguration getBalConfiguration() {
    return BalConfiguration.DEFAULT;
  }

  protected ExecutionContextTestFixture createFreshContext() {
    return ExecutionContextTestFixture.builder(GenesisConfig.fromResource(GENESIS_CONFIG))
        .dataStorageFormat(DataStorageFormat.BONSAI)
        .build();
  }

  protected BlockProcessor createSequentialProcessor(final ExecutionContextTestFixture ctx) {
    final ProtocolSpec spec =
        ctx.getProtocolSchedule()
            .getByBlockHeader(new BlockHeaderTestFixture().number(0L).buildHeader());
    return new MainnetBlockProcessor(
        spec.getTransactionProcessor(),
        spec.getTransactionReceiptFactory(),
        Wei.ZERO,
        BlockHeader::getCoinbase,
        true,
        ctx.getProtocolSchedule(),
        SEQUENTIAL_CONFIG);
  }

  /**
   * Creates a parallel block processor WITHOUT the block-level fallback that
   * MainnetParallelBlockProcessor normally provides. This ensures that if parallel execution
   * produces an incorrect state root (e.g. due to broken collision detection), the test fails
   * instead of silently falling back to sequential processing.
   *
   * <p>The transaction-level fallback is preserved: transactions whose pre-computed result is
   * unavailable (nonce mismatch, pre-computation failure) are still re-executed sequentially.
   */
  protected BlockProcessor createParallelProcessor(final ExecutionContextTestFixture ctx) {
    final ProtocolSpec spec =
        ctx.getProtocolSchedule()
            .getByBlockHeader(new BlockHeaderTestFixture().number(0L).buildHeader());
    return new NoBlockFallbackParallelBlockProcessor(
        spec.getTransactionProcessor(),
        spec.getTransactionReceiptFactory(),
        Wei.ZERO,
        BlockHeader::getCoinbase,
        true,
        ctx.getProtocolSchedule(),
        getBalConfiguration(),
        new NoOpMetricsSystem());
  }

  /**
   * Parallel block processor that extends {@link MainnetParallelBlockProcessor} but disables the
   * block-level fallback. When parallel processing produces a wrong state root, the result is
   * returned as-is (failed), rather than being masked by a silent sequential re-execution.
   */
  static class NoBlockFallbackParallelBlockProcessor extends MainnetParallelBlockProcessor {

    public NoBlockFallbackParallelBlockProcessor(
        final MainnetTransactionProcessor transactionProcessor,
        final TransactionReceiptFactory transactionReceiptFactory,
        final Wei blockReward,
        final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        final boolean skipZeroBlockRewards,
        final ProtocolSchedule protocolSchedule,
        final BalConfiguration balConfiguration,
        final MetricsSystem metricsSystem) {
      super(
          transactionProcessor,
          transactionReceiptFactory,
          blockReward,
          miningBeneficiaryCalculator,
          skipZeroBlockRewards,
          protocolSchedule,
          balConfiguration,
          metricsSystem);
    }

    @Override
    public BlockProcessingResult processBlock(
        final ProtocolContext protocolContext,
        final Blockchain blockchain,
        final MutableWorldState worldState,
        final Block block,
        final Optional<BlockAccessList> blockAccessList,
        final PreprocessingFunction preprocessingBlockFunction) {
      if (preprocessingBlockFunction instanceof PreprocessingFunction.NoPreprocessing) {
        return BlockProcessingResult.FAILED;
      } else {
        return super.processBlock(
            protocolContext,
            blockchain,
            worldState,
            block,
            blockAccessList,
            preprocessingBlockFunction);
      }
    }
  }

  // ==================== Block Construction ====================

  protected Block createBlock(
      final ExecutionContextTestFixture ctx,
      final Hash stateRoot,
      final Wei baseFee,
      final Transaction... txs) {
    final BlockHeader parentHeader = ctx.getBlockchain().getChainHeadHeader();
    final BlockHeader blockHeader =
        new BlockHeaderTestFixture()
            .number(parentHeader.getNumber() + 1L)
            .parentHash(parentHeader.getHash())
            .coinbase(MINING_BENEFICIARY)
            .stateRoot(stateRoot)
            .gasLimit(30_000_000L)
            .baseFeePerGas(baseFee)
            .buildHeader();
    final BlockBody blockBody =
        new BlockBody(Arrays.asList(txs), Collections.emptyList(), Optional.empty());
    return new Block(blockHeader, blockBody);
  }

  // ==================== State Root Discovery ====================

  /**
   * Discovers the correct state root by running sequential processing with a dummy root. The
   * processing fails at persist time due to state root mismatch, and the actual computed root is
   * extracted from the error message.
   */
  protected Hash discoverStateRoot(final Wei baseFee, final Transaction... txs) {
    final ExecutionContextTestFixture ctx = createFreshContext();
    final MutableWorldState ws = ctx.getStateArchive().getWorldState();
    final Block block = createBlock(ctx, Hash.ZERO, baseFee, txs);
    final BlockProcessor processor = createSequentialProcessor(ctx);
    final BlockProcessingResult result =
        processor.processBlock(ctx.getProtocolContext(), ctx.getBlockchain(), ws, block);

    if (result.isSuccessful()) {
      return ws.rootHash();
    }

    final String msg =
        result.errorMessage.orElseThrow(
            () -> new AssertionError("Discovery processing failed without error message"));
    final String marker = "calculated ";
    final int idx = msg.indexOf(marker);
    if (idx < 0) {
      throw new AssertionError("Unexpected error message format: " + msg);
    }
    return Hash.fromHexString(msg.substring(idx + marker.length()));
  }

  // ==================== Core Comparison Logic ====================

  /**
   * Processes the given transactions both sequentially and in parallel, then asserts that the
   * resulting state roots are identical. Subclasses may override to provide strategy-specific
   * comparison logic (e.g., BAL injection).
   */
  protected ComparisonResult executeAndCompare(final Wei baseFee, final Transaction... txs) {
    final Hash stateRoot = discoverStateRoot(baseFee, txs);

    // Sequential processing
    final ExecutionContextTestFixture seqCtx = createFreshContext();
    final MutableWorldState seqWs = seqCtx.getStateArchive().getWorldState();
    final Block block = createBlock(seqCtx, stateRoot, baseFee, txs);
    final BlockProcessor seqProcessor = createSequentialProcessor(seqCtx);
    final BlockProcessingResult seqResult =
        seqProcessor.processBlock(
            seqCtx.getProtocolContext(), seqCtx.getBlockchain(), seqWs, block);
    assertTrue(
        seqResult.isSuccessful(),
        "Sequential processing failed: " + seqResult.errorMessage.orElse("(no message)"));

    // Parallel processing
    final ExecutionContextTestFixture parCtx = createFreshContext();
    final MutableWorldState parWs = parCtx.getStateArchive().getWorldState();
    final Block parBlock = createBlock(parCtx, stateRoot, baseFee, txs);
    final ProtocolSpec spec =
        parCtx
            .getProtocolSchedule()
            .getByBlockHeader(new BlockHeaderTestFixture().number(0L).buildHeader());
    final BlockProcessor parProcessor = createParallelProcessor(parCtx);
    final ParallelTransactionPreprocessing preprocessing =
        createParallelPreprocessing(spec.getTransactionProcessor());
    final BlockProcessingResult parResult =
        parProcessor.processBlock(
            parCtx.getProtocolContext(), parCtx.getBlockchain(), parWs, parBlock, preprocessing);
    assertTrue(
        parResult.isSuccessful(),
        getVariantName()
            + " parallel processing failed: "
            + parResult.errorMessage.orElse("(no message)"));

    // State root comparison
    assertThat(parWs.rootHash())
        .as(getVariantName() + " parallel state root must match sequential")
        .isEqualTo(seqWs.rootHash());

    // BAL hash comparison
    final Optional<BlockAccessList> seqBal = getBlockAccessList(seqResult);
    final Optional<BlockAccessList> parBal = getBlockAccessList(parResult);
    assertThat(seqBal).as("Sequential BAL should be present").isPresent();
    assertThat(parBal).as(getVariantName() + " parallel BAL should be present").isPresent();
    assertThat(BodyValidation.balHash(parBal.get()))
        .as(getVariantName() + " parallel BAL hash must match sequential")
        .isEqualTo(BodyValidation.balHash(seqBal.get()));

    return new ComparisonResult(
        seqWs.rootHash(), parWs.rootHash(), seqResult, parResult, seqWs, parWs);
  }

  // ==================== Transaction Builders ====================

  protected Transaction createTransferTransaction(
      final long nonce,
      final long value,
      final long gasLimit,
      final long maxPriorityFeePerGas,
      final long maxFeePerGas,
      final String toAddress,
      final KeyPair keyPair) {
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .nonce(nonce)
        .maxPriorityFeePerGas(Wei.of(maxPriorityFeePerGas))
        .maxFeePerGas(Wei.of(maxFeePerGas))
        .gasLimit(gasLimit)
        .to(Address.fromHexStringStrict(toAddress))
        .value(Wei.of(value))
        .payload(Bytes.EMPTY)
        .chainId(BigInteger.valueOf(42))
        .signAndBuild(keyPair);
  }

  protected Transaction createContractCallTransaction(
      final int nonce,
      final Address contractAddress,
      final String methodName,
      final KeyPair keyPair,
      final Optional<Integer> value) {
    final Bytes payload = encodeFunctionCall(methodName, value);
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .nonce(nonce)
        .maxPriorityFeePerGas(Wei.of(0))
        .maxFeePerGas(Wei.of(5))
        .gasLimit(3_000_000L)
        .to(contractAddress)
        .value(Wei.ZERO)
        .payload(payload)
        .chainId(BigInteger.valueOf(42))
        .signAndBuild(keyPair);
  }

  protected Transaction createContractSendEthTransaction(
      final int nonce,
      final Address contractAddress,
      final String methodName,
      final KeyPair keyPair,
      final String toAddress,
      final long value) {
    final List<Type> inputParameters =
        Arrays.asList(new org.web3j.abi.datatypes.Address(toAddress), new Uint256(value));
    final Function function = new Function(methodName, inputParameters, List.of());
    final Bytes payload = Bytes.fromHexString(FunctionEncoder.encode(function));
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .nonce(nonce)
        .maxPriorityFeePerGas(Wei.of(0))
        .maxFeePerGas(Wei.of(5))
        .gasLimit(3_000_000L)
        .to(contractAddress)
        .value(Wei.ZERO)
        .payload(payload)
        .chainId(BigInteger.valueOf(42))
        .signAndBuild(keyPair);
  }

  // ==================== Helpers ====================

  private Bytes encodeFunctionCall(final String methodName, final Optional<Integer> value) {
    final List<Type> inputParameters =
        value.isPresent() ? Arrays.<Type>asList(new Uint256(value.get())) : List.of();
    final Function function = new Function(methodName, inputParameters, List.of());
    return Bytes.fromHexString(FunctionEncoder.encode(function));
  }

  protected void assertAccountsMatch(
      final MutableWorldState seqWs, final MutableWorldState parWs, final Address address) {
    final BonsaiAccount seqAccount = (BonsaiAccount) seqWs.get(address);
    final BonsaiAccount parAccount = (BonsaiAccount) parWs.get(address);

    if (seqAccount == null) {
      assertThat(parAccount).as("Account " + address + " should be null in both").isNull();
      return;
    }
    assertThat(parAccount).as("Account " + address + " should exist in parallel").isNotNull();
    assertThat(parAccount.getBalance())
        .as("Balance mismatch for " + address)
        .isEqualTo(seqAccount.getBalance());
    assertThat(parAccount.getNonce())
        .as("Nonce mismatch for " + address)
        .isEqualTo(seqAccount.getNonce());
  }

  protected void assertContractStorageMatches(
      final MutableWorldState seqWs,
      final MutableWorldState parWs,
      final Address contractAddress,
      final int slot) {
    final BonsaiAccount seqAccount = (BonsaiAccount) seqWs.get(contractAddress);
    final BonsaiAccount parAccount = (BonsaiAccount) parWs.get(contractAddress);
    assertThat(parAccount.getStorageValue(UInt256.valueOf(slot)))
        .as("Storage slot " + slot + " mismatch for " + contractAddress)
        .isEqualTo(seqAccount.getStorageValue(UInt256.valueOf(slot)));
  }

  protected void assertContractStorage(
      final MutableWorldState worldState,
      final Address contractAddress,
      final int slot,
      final int expectedValue) {
    final BonsaiAccount account = (BonsaiAccount) worldState.get(contractAddress);
    assertThat(account).as("Contract account should exist: " + contractAddress).isNotNull();
    assertThat(account.getStorageValue(UInt256.valueOf(slot)))
        .as("Storage slot " + slot + " value")
        .isEqualTo(UInt256.valueOf(expectedValue));
  }

  protected Optional<BlockAccessList> getBlockAccessList(final BlockProcessingResult result) {
    return result.getYield().flatMap(BlockProcessingOutputs::getBlockAccessList);
  }

  protected static KeyPair generateKeyPair(final String privateKeyHex) {
    return SignatureAlgorithmFactory.getInstance()
        .createKeyPair(
            SECPPrivateKey.create(
                Bytes32.fromHexString(privateKeyHex), SignatureAlgorithm.ALGORITHM));
  }

  // ==================== Result Record ====================

  protected record ComparisonResult(
      Hash seqStateRoot,
      Hash parStateRoot,
      BlockProcessingResult seqResult,
      BlockProcessingResult parResult,
      MutableWorldState seqWorldState,
      MutableWorldState parWorldState) {}

  // ==================== Test Cases ====================

  @Nested
  @DisplayName("Simple Transfers")
  class SimpleTransferTests {

    @Test
    @DisplayName("Independent transfers from different senders produce matching state")
    void independentTransfers() {
      final Transaction tx1 =
          createTransferTransaction(
              0,
              1_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_2,
              ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction tx2 =
          createTransferTransaction(
              0,
              2_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_3,
              ACCOUNT_GENESIS_2_KEYPAIR);

      final ComparisonResult result = executeAndCompare(Wei.of(5), tx1, tx2);

      final Address addr2 = Address.fromHexStringStrict(ACCOUNT_2);
      final Address addr3 = Address.fromHexStringStrict(ACCOUNT_3);
      final Address sender1 = Address.fromHexStringStrict(ACCOUNT_GENESIS_1);
      final Address sender2 = Address.fromHexStringStrict(ACCOUNT_GENESIS_2);

      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr2);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr3);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), sender1);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), sender2);

      final BonsaiAccount seqAcct2 = (BonsaiAccount) result.seqWorldState().get(addr2);
      assertThat(seqAcct2.getBalance()).isEqualTo(Wei.of(1_000_000_000_000_000_000L));
      final BonsaiAccount seqAcct3 = (BonsaiAccount) result.seqWorldState().get(addr3);
      assertThat(seqAcct3.getBalance()).isEqualTo(Wei.of(2_000_000_000_000_000_000L));
    }

    @Test
    @DisplayName("Multiple transactions from the same sender produce matching state")
    void sameSenderConflict() {
      final Transaction tx1 =
          createTransferTransaction(
              0,
              1_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_4,
              ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction tx2 =
          createTransferTransaction(
              1,
              2_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_5,
              ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction tx3 =
          createTransferTransaction(
              2,
              3_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_6,
              ACCOUNT_GENESIS_1_KEYPAIR);

      final ComparisonResult result = executeAndCompare(Wei.of(5), tx1, tx2, tx3);

      final Address addr4 = Address.fromHexStringStrict(ACCOUNT_4);
      final Address addr5 = Address.fromHexStringStrict(ACCOUNT_5);
      final Address addr6 = Address.fromHexStringStrict(ACCOUNT_6);
      final Address sender = Address.fromHexStringStrict(ACCOUNT_GENESIS_1);

      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr4);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr5);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr6);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), sender);

      assertThat(((BonsaiAccount) result.seqWorldState().get(addr4)).getBalance())
          .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
      assertThat(((BonsaiAccount) result.seqWorldState().get(addr5)).getBalance())
          .isEqualTo(Wei.of(2_000_000_000_000_000_000L));
      assertThat(((BonsaiAccount) result.seqWorldState().get(addr6)).getBalance())
          .isEqualTo(Wei.of(3_000_000_000_000_000_000L));

      assertThat(((BonsaiAccount) result.seqWorldState().get(sender)).getNonce()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Receiver of tx1 is sender of tx2 produces matching state")
    void receiverSenderOverlap() {
      final Transaction tx1 =
          createTransferTransaction(
              0,
              1_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_GENESIS_2,
              ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction tx2 =
          createTransferTransaction(
              0,
              2_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_2,
              ACCOUNT_GENESIS_2_KEYPAIR);

      final ComparisonResult result = executeAndCompare(Wei.of(5), tx1, tx2);

      final Address addr2 = Address.fromHexStringStrict(ACCOUNT_2);
      final Address sender1 = Address.fromHexStringStrict(ACCOUNT_GENESIS_1);
      final Address sender2 = Address.fromHexStringStrict(ACCOUNT_GENESIS_2);

      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr2);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), sender1);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), sender2);

      assertThat(((BonsaiAccount) result.seqWorldState().get(addr2)).getBalance())
          .isEqualTo(Wei.of(2_000_000_000_000_000_000L));
      assertThat(((BonsaiAccount) result.seqWorldState().get(sender1)).getNonce()).isEqualTo(1L);
      assertThat(((BonsaiAccount) result.seqWorldState().get(sender2)).getNonce()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Single transaction produces matching state")
    void singleTransaction() {
      final Transaction tx1 =
          createTransferTransaction(
              0,
              5_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_2,
              ACCOUNT_GENESIS_1_KEYPAIR);

      final ComparisonResult result = executeAndCompare(Wei.of(5), tx1);

      final Address addr2 = Address.fromHexStringStrict(ACCOUNT_2);
      final Address sender = Address.fromHexStringStrict(ACCOUNT_GENESIS_1);

      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr2);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), sender);

      assertThat(((BonsaiAccount) result.seqWorldState().get(addr2)).getBalance())
          .isEqualTo(Wei.of(5_000_000_000_000_000_000L));
    }

    @Test
    @DisplayName("Both senders send to the same recipient produce matching state")
    void sameRecipientFromDifferentSenders() {
      final Transaction tx1 =
          createTransferTransaction(
              0,
              1_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_2,
              ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction tx2 =
          createTransferTransaction(
              0,
              3_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_2,
              ACCOUNT_GENESIS_2_KEYPAIR);

      final ComparisonResult result = executeAndCompare(Wei.of(5), tx1, tx2);

      final Address addr2 = Address.fromHexStringStrict(ACCOUNT_2);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr2);
      assertThat(((BonsaiAccount) result.seqWorldState().get(addr2)).getBalance())
          .isEqualTo(Wei.of(4_000_000_000_000_000_000L));
    }
  }

  @Nested
  @DisplayName("Contract Storage")
  class ContractStorageTests {

    private final Address contractAddr = Address.fromHexStringStrict(CONTRACT_ADDRESS);

    @Test
    @DisplayName("Writing different storage slots from the same sender produces matching state")
    void writeMultipleSlotsSameSender() {
      final Transaction txSetSlot1 =
          createContractCallTransaction(
              0, contractAddr, "setSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(100));
      final Transaction txSetSlot2 =
          createContractCallTransaction(
              1, contractAddr, "setSlot2", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(200));
      final Transaction txSetSlot3 =
          createContractCallTransaction(
              2, contractAddr, "setSlot3", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(300));

      final ComparisonResult result =
          executeAndCompare(Wei.of(5), txSetSlot1, txSetSlot2, txSetSlot3);

      assertContractStorage(result.seqWorldState(), contractAddr, 0, 100);
      assertContractStorage(result.seqWorldState(), contractAddr, 1, 200);
      assertContractStorage(result.seqWorldState(), contractAddr, 2, 300);

      assertContractStorageMatches(result.seqWorldState(), result.parWorldState(), contractAddr, 0);
      assertContractStorageMatches(result.seqWorldState(), result.parWorldState(), contractAddr, 1);
      assertContractStorageMatches(result.seqWorldState(), result.parWorldState(), contractAddr, 2);
      assertAccountsMatch(
          result.seqWorldState(),
          result.parWorldState(),
          Address.fromHexStringStrict(ACCOUNT_GENESIS_1));
    }

    @Test
    @DisplayName("Writing then reading the same storage slot produces matching state")
    void writeThenReadSameSlot() {
      final Transaction txSetSlot1 =
          createContractCallTransaction(
              0, contractAddr, "setSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(999));
      final Transaction txGetSlot1 =
          createContractCallTransaction(
              0, contractAddr, "getSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.empty());

      final ComparisonResult result = executeAndCompare(Wei.of(5), txSetSlot1, txGetSlot1);

      assertContractStorage(result.seqWorldState(), contractAddr, 0, 999);
      assertContractStorageMatches(result.seqWorldState(), result.parWorldState(), contractAddr, 0);
      assertAccountsMatch(
          result.seqWorldState(),
          result.parWorldState(),
          Address.fromHexStringStrict(ACCOUNT_GENESIS_1));
      assertAccountsMatch(
          result.seqWorldState(),
          result.parWorldState(),
          Address.fromHexStringStrict(ACCOUNT_GENESIS_2));
    }

    @Test
    @DisplayName("Reading then writing the same storage slot produces matching state")
    void readThenWriteSameSlot() {
      final Transaction txGetSlot1 =
          createContractCallTransaction(
              0, contractAddr, "getSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.empty());
      final Transaction txSetSlot1 =
          createContractCallTransaction(
              0, contractAddr, "setSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.of(777));

      final ComparisonResult result = executeAndCompare(Wei.of(5), txGetSlot1, txSetSlot1);

      assertContractStorage(result.seqWorldState(), contractAddr, 0, 777);
      assertContractStorageMatches(result.seqWorldState(), result.parWorldState(), contractAddr, 0);
    }

    @Test
    @DisplayName("Write-read-write across two senders produces matching state")
    void writeReadWriteAcrossSenders() {
      final Transaction txSetSlot1 =
          createContractCallTransaction(
              0, contractAddr, "setSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(100));
      final Transaction txGetSlot1 =
          createContractCallTransaction(
              0, contractAddr, "getSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.empty());
      final Transaction txSetSlot2 =
          createContractCallTransaction(
              1, contractAddr, "setSlot2", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(200));
      final Transaction txSetSlot3 =
          createContractCallTransaction(
              2, contractAddr, "setSlot3", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(300));

      final ComparisonResult result =
          executeAndCompare(Wei.of(5), txSetSlot1, txGetSlot1, txSetSlot2, txSetSlot3);

      assertContractStorage(result.seqWorldState(), contractAddr, 0, 100);
      assertContractStorage(result.seqWorldState(), contractAddr, 1, 200);
      assertContractStorage(result.seqWorldState(), contractAddr, 2, 300);

      for (int slot = 0; slot <= 2; slot++) {
        assertContractStorageMatches(
            result.seqWorldState(), result.parWorldState(), contractAddr, slot);
      }
      assertAccountsMatch(
          result.seqWorldState(),
          result.parWorldState(),
          Address.fromHexStringStrict(ACCOUNT_GENESIS_1));
      assertAccountsMatch(
          result.seqWorldState(),
          result.parWorldState(),
          Address.fromHexStringStrict(ACCOUNT_GENESIS_2));
    }

    @Test
    @DisplayName("Storage reads from different senders produce matching state")
    void readFromDifferentSenders() {
      final Transaction txGetSlot1A =
          createContractCallTransaction(
              0, contractAddr, "getSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.empty());
      final Transaction txGetSlot1B =
          createContractCallTransaction(
              0, contractAddr, "getSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.empty());

      final ComparisonResult result = executeAndCompare(Wei.of(5), txGetSlot1A, txGetSlot1B);

      assertContractStorageMatches(result.seqWorldState(), result.parWorldState(), contractAddr, 0);
      assertAccountsMatch(
          result.seqWorldState(),
          result.parWorldState(),
          Address.fromHexStringStrict(ACCOUNT_GENESIS_1));
      assertAccountsMatch(
          result.seqWorldState(),
          result.parWorldState(),
          Address.fromHexStringStrict(ACCOUNT_GENESIS_2));
    }
  }

  @Nested
  @DisplayName("Mixed Transfers and Contract Interactions")
  class MixedTests {

    private final Address contractAddr = Address.fromHexStringStrict(CONTRACT_ADDRESS);

    @Test
    @DisplayName("Transfer + contract storage write from different senders produce matching state")
    void transferAndContractWrite() {
      final Transaction txTransfer =
          createTransferTransaction(
              0,
              1_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_2,
              ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction txSetSlot =
          createContractCallTransaction(
              0, contractAddr, "setSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.of(42));

      final ComparisonResult result = executeAndCompare(Wei.of(5), txTransfer, txSetSlot);

      final Address addr2 = Address.fromHexStringStrict(ACCOUNT_2);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr2);
      assertThat(((BonsaiAccount) result.seqWorldState().get(addr2)).getBalance())
          .isEqualTo(Wei.of(1_000_000_000_000_000_000L));

      assertContractStorage(result.seqWorldState(), contractAddr, 0, 42);
      assertContractStorageMatches(result.seqWorldState(), result.parWorldState(), contractAddr, 0);
    }

    @Test
    @DisplayName("Transfer to contract + contract send ETH produce matching state")
    void transferToContractThenContractSendsEth() {
      final Transaction txFundContract =
          createTransferTransaction(
              0,
              1_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              CONTRACT_ADDRESS,
              ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction txSendFromContract =
          createContractSendEthTransaction(
              0,
              contractAddr,
              "transferTo",
              ACCOUNT_GENESIS_2_KEYPAIR,
              ACCOUNT_2,
              500_000_000_000_000_000L);

      final ComparisonResult result =
          executeAndCompare(Wei.of(5), txFundContract, txSendFromContract);

      final Address addr2 = Address.fromHexStringStrict(ACCOUNT_2);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr2);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), contractAddr);

      assertThat(((BonsaiAccount) result.seqWorldState().get(contractAddr)).getBalance())
          .isEqualTo(Wei.of(500_000_000_000_000_000L));
      assertThat(((BonsaiAccount) result.seqWorldState().get(addr2)).getBalance())
          .isEqualTo(Wei.of(500_000_000_000_000_000L));
    }

    @Test
    @DisplayName("Transfer + contract write + read from different senders produce matching state")
    void transferAndContractWriteAndRead() {
      final Transaction txTransfer =
          createTransferTransaction(
              0,
              1_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_3,
              ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction txSetSlot1 =
          createContractCallTransaction(
              1, contractAddr, "setSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(555));
      final Transaction txGetSlot1 =
          createContractCallTransaction(
              0, contractAddr, "getSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.empty());

      final ComparisonResult result =
          executeAndCompare(Wei.of(5), txTransfer, txSetSlot1, txGetSlot1);

      final Address addr3 = Address.fromHexStringStrict(ACCOUNT_3);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr3);
      assertThat(((BonsaiAccount) result.seqWorldState().get(addr3)).getBalance())
          .isEqualTo(Wei.of(1_000_000_000_000_000_000L));

      assertContractStorage(result.seqWorldState(), contractAddr, 0, 555);
      assertContractStorageMatches(result.seqWorldState(), result.parWorldState(), contractAddr, 0);

      assertAccountsMatch(
          result.seqWorldState(),
          result.parWorldState(),
          Address.fromHexStringStrict(ACCOUNT_GENESIS_1));
      assertAccountsMatch(
          result.seqWorldState(),
          result.parWorldState(),
          Address.fromHexStringStrict(ACCOUNT_GENESIS_2));
    }

    @Test
    @DisplayName("Multiple transfers + multiple contract writes produce matching state")
    void multipleTransfersAndContractWrites() {
      final Transaction txTransfer1 =
          createTransferTransaction(
              0,
              1_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_2,
              ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction txTransfer2 =
          createTransferTransaction(
              0,
              2_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_3,
              ACCOUNT_GENESIS_2_KEYPAIR);
      final Transaction txSetSlot1 =
          createContractCallTransaction(
              1, contractAddr, "setSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(11));
      final Transaction txSetSlot2 =
          createContractCallTransaction(
              1, contractAddr, "setSlot2", ACCOUNT_GENESIS_2_KEYPAIR, Optional.of(22));

      final ComparisonResult result =
          executeAndCompare(Wei.of(5), txTransfer1, txTransfer2, txSetSlot1, txSetSlot2);

      final Address addr2 = Address.fromHexStringStrict(ACCOUNT_2);
      final Address addr3 = Address.fromHexStringStrict(ACCOUNT_3);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr2);
      assertAccountsMatch(result.seqWorldState(), result.parWorldState(), addr3);

      assertThat(((BonsaiAccount) result.seqWorldState().get(addr2)).getBalance())
          .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
      assertThat(((BonsaiAccount) result.seqWorldState().get(addr3)).getBalance())
          .isEqualTo(Wei.of(2_000_000_000_000_000_000L));

      assertContractStorage(result.seqWorldState(), contractAddr, 0, 11);
      assertContractStorage(result.seqWorldState(), contractAddr, 1, 22);
      assertContractStorageMatches(result.seqWorldState(), result.parWorldState(), contractAddr, 0);
      assertContractStorageMatches(result.seqWorldState(), result.parWorldState(), contractAddr, 1);
    }
  }

  @Nested
  @DisplayName("Re-execution Consistency")
  class ReExecutionTests {

    @Test
    @DisplayName("Two parallel executions of the same block produce identical state roots")
    void parallelReExecutionIsDeterministic() {
      final Transaction tx1 =
          createTransferTransaction(
              0,
              1_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_2,
              ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction tx2 =
          createTransferTransaction(
              0,
              2_000_000_000_000_000_000L,
              300_000L,
              0L,
              5L,
              ACCOUNT_3,
              ACCOUNT_GENESIS_2_KEYPAIR);

      final Hash stateRoot = discoverStateRoot(Wei.of(5), tx1, tx2);

      // First parallel execution
      final ExecutionContextTestFixture ctx1 = createFreshContext();
      final MutableWorldState ws1 = ctx1.getStateArchive().getWorldState();
      final Block block1 = createBlock(ctx1, stateRoot, Wei.of(5), tx1, tx2);
      final ProtocolSpec spec1 =
          ctx1.getProtocolSchedule()
              .getByBlockHeader(new BlockHeaderTestFixture().number(0L).buildHeader());
      final BlockProcessor proc1 = createParallelProcessor(ctx1);
      final BlockProcessingResult result1 =
          proc1.processBlock(
              ctx1.getProtocolContext(),
              ctx1.getBlockchain(),
              ws1,
              block1,
              createParallelPreprocessing(spec1.getTransactionProcessor()));
      assertTrue(result1.isSuccessful(), "First parallel execution failed");

      // Second parallel execution
      final ExecutionContextTestFixture ctx2 = createFreshContext();
      final MutableWorldState ws2 = ctx2.getStateArchive().getWorldState();
      final Block block2 = createBlock(ctx2, stateRoot, Wei.of(5), tx1, tx2);
      final ProtocolSpec spec2 =
          ctx2.getProtocolSchedule()
              .getByBlockHeader(new BlockHeaderTestFixture().number(0L).buildHeader());
      final BlockProcessor proc2 = createParallelProcessor(ctx2);
      final BlockProcessingResult result2 =
          proc2.processBlock(
              ctx2.getProtocolContext(),
              ctx2.getBlockchain(),
              ws2,
              block2,
              createParallelPreprocessing(spec2.getTransactionProcessor()));
      assertTrue(result2.isSuccessful(), "Second parallel execution failed");

      assertThat(ws2.rootHash())
          .as("Re-execution must produce identical state root")
          .isEqualTo(ws1.rootHash());
    }
  }

  /**
   * Tests that exercise real read-after-write dependencies between different senders. These tests
   * MUST fail if collision detection is disabled (hasCollision always returns false) because:
   *
   * <ul>
   *   <li>Both transactions are valid when pre-computed against the base state (different senders →
   *       no nonce conflict, sufficient balance)
   *   <li>But the pre-computed result for Tx2 is WRONG because it reads stale state (base state
   *       instead of the state after Tx1)
   *   <li>importStateChangesFromSource overwrites slot values, so the wrong value persists
   * </ul>
   *
   * <p>Uses the ParallelTestStorage contract at 0x...eeeee which has incrementSlot1() that reads
   * slot 0 and writes slot0 + 1. Initial slot 0 value = 10.
   */
  @Nested
  @DisplayName("Storage Dependency (collision detection validation)")
  class StorageDependencyTests {

    private final Address parallelContract = Address.fromHexStringStrict(PARALLEL_TEST_CONTRACT);

    @Test
    @DisplayName("setSlot then increment from different sender must detect collision")
    void setSlotThenIncrementFromDifferentSender() {
      // Tx1: sender A sets slot 0 = 100
      // Tx2: sender B increments slot 0 (reads slot 0, writes slot 0 + 1)
      // Sequential result: slot 0 = 101 (set to 100, then 100 + 1)
      // Without collision detection: slot 0 = 11 (base=10, pre-computed 10+1, overwrites 100)
      final Transaction txSet =
          createContractCallTransaction(
              0, parallelContract, "setSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(100));
      final Transaction txIncrement =
          createContractCallTransaction(
              0, parallelContract, "incrementSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.empty());

      final ComparisonResult result = executeAndCompare(Wei.of(5), txSet, txIncrement);

      assertContractStorage(result.seqWorldState(), parallelContract, 0, 101);
      assertContractStorageMatches(
          result.seqWorldState(), result.parWorldState(), parallelContract, 0);
    }

    @Test
    @DisplayName("Two increments from different senders must detect collision")
    void twoIncrementsFromDifferentSenders() {
      // Tx1: sender A increments slot 0 (10 → 11)
      // Tx2: sender B increments slot 0 (11 → 12)
      // Sequential result: slot 0 = 12
      // Without collision detection: both see base=10, both write 11, last import wins → 11
      final Transaction txInc1 =
          createContractCallTransaction(
              0, parallelContract, "incrementSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.empty());
      final Transaction txInc2 =
          createContractCallTransaction(
              0, parallelContract, "incrementSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.empty());

      final ComparisonResult result = executeAndCompare(Wei.of(5), txInc1, txInc2);

      assertContractStorage(result.seqWorldState(), parallelContract, 0, 12);
      assertContractStorageMatches(
          result.seqWorldState(), result.parWorldState(), parallelContract, 0);
    }

    @Test
    @DisplayName("Increment then set from different sender must detect collision")
    void incrementThenSetFromDifferentSender() {
      // Tx1: sender A increments slot 0 (10 → 11)
      // Tx2: sender B sets slot 0 = 500
      // Sequential result: slot 0 = 500
      // This might pass without collision detection (set is absolute), but gas costs
      // could differ since SSTORE pricing depends on current vs original values.
      final Transaction txInc =
          createContractCallTransaction(
              0, parallelContract, "incrementSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.empty());
      final Transaction txSet =
          createContractCallTransaction(
              0, parallelContract, "setSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.of(500));

      final ComparisonResult result = executeAndCompare(Wei.of(5), txInc, txSet);

      assertContractStorage(result.seqWorldState(), parallelContract, 0, 500);
      assertContractStorageMatches(
          result.seqWorldState(), result.parWorldState(), parallelContract, 0);
      assertAccountsMatch(
          result.seqWorldState(),
          result.parWorldState(),
          Address.fromHexStringStrict(ACCOUNT_GENESIS_1));
      assertAccountsMatch(
          result.seqWorldState(),
          result.parWorldState(),
          Address.fromHexStringStrict(ACCOUNT_GENESIS_2));
    }

    @Test
    @DisplayName("Set slot + increment + set another slot must detect collision on shared slot")
    void setIncrementAndWriteOtherSlot() {
      // Tx1: sender A sets slot 0 = 200 on ParallelTestStorage
      // Tx2: sender B increments slot 0 (should read 200, write 201)
      // Tx3: sender A sets slot 1 = 42 on the other contract (no conflict)
      final Transaction txSet =
          createContractCallTransaction(
              0, parallelContract, "setSlot1", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(200));
      final Transaction txIncrement =
          createContractCallTransaction(
              0, parallelContract, "incrementSlot1", ACCOUNT_GENESIS_2_KEYPAIR, Optional.empty());
      final Address contractAddr = Address.fromHexStringStrict(CONTRACT_ADDRESS);
      final Transaction txOther =
          createContractCallTransaction(
              1, contractAddr, "setSlot2", ACCOUNT_GENESIS_1_KEYPAIR, Optional.of(42));

      final ComparisonResult result = executeAndCompare(Wei.of(5), txSet, txIncrement, txOther);

      assertContractStorage(result.seqWorldState(), parallelContract, 0, 201);
      assertContractStorageMatches(
          result.seqWorldState(), result.parWorldState(), parallelContract, 0);
      assertContractStorage(result.seqWorldState(), contractAddr, 1, 42);
      assertContractStorageMatches(result.seqWorldState(), result.parWorldState(), contractAddr, 1);
    }
  }

  @Nested
  @DisplayName("Mining beneficiary BAL balance tracking")
  class MiningBeneficiaryBalTests {

    @Test
    @DisplayName(
        "Parallel BAL must record correct cumulative mining beneficiary postBalance with priority fees")
    void miningBeneficiaryPostBalanceWithPriorityFees() {
      // Two independent transfers with non-zero priority fees.
      // baseFee=1 so effectivePriorityFee = min(maxPriorityFeePerGas, maxFeePerGas - baseFee) > 0.
      // The mining beneficiary accumulates the priority fee from each transaction.
      // The setPostBalance code in getProcessingResult must update the BAL entry
      // so that each BalanceChange reflects the cumulative balance, not the isolated one.
      final Transaction tx1 =
          createTransferTransaction(
              0,
              1_000_000_000_000_000_000L,
              300_000L,
              2L,
              10L,
              ACCOUNT_2,
              ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction tx2 =
          createTransferTransaction(
              0,
              2_000_000_000_000_000_000L,
              300_000L,
              3L,
              10L,
              ACCOUNT_3,
              ACCOUNT_GENESIS_2_KEYPAIR);

      final ComparisonResult result = executeAndCompare(Wei.of(1), tx1, tx2);

      final Optional<BlockAccessList> seqBal = getBlockAccessList(result.seqResult());
      final Optional<BlockAccessList> parBal = getBlockAccessList(result.parResult());
      assertThat(seqBal).as("Sequential BAL should be present").isPresent();
      assertThat(parBal).as("Parallel BAL should be present").isPresent();

      final List<BlockAccessList.BalanceChange> seqBalChanges =
          getBalanceChangesFor(seqBal.get(), MINING_BENEFICIARY);
      final List<BlockAccessList.BalanceChange> parBalChanges =
          getBalanceChangesFor(parBal.get(), MINING_BENEFICIARY);

      assertThat(seqBalChanges)
          .as("Sequential BAL should have balance changes for mining beneficiary")
          .isNotEmpty();
      assertThat(parBalChanges)
          .as("Parallel BAL balance changes for mining beneficiary must match sequential")
          .isEqualTo(seqBalChanges);
    }

    @Test
    @DisplayName(
        "Multiple transactions with varying priority fees produce correct cumulative beneficiary balances")
    void multipleTxsWithVaryingPriorityFees() {
      // Three transactions: two from sender A (nonces 0,1) and one from sender B (nonce 0),
      // each with a different priority fee. baseFee=1 ensures positive effective priority fees.
      // The BAL must record the correct post-balance for the mining beneficiary after each tx.
      final Transaction tx1 =
          createTransferTransaction(
              0, 100_000_000_000_000L, 300_000L, 1L, 10L, ACCOUNT_2, ACCOUNT_GENESIS_1_KEYPAIR);
      final Transaction tx2 =
          createTransferTransaction(
              0, 200_000_000_000_000L, 300_000L, 4L, 10L, ACCOUNT_3, ACCOUNT_GENESIS_2_KEYPAIR);
      final Transaction tx3 =
          createTransferTransaction(
              1, 300_000_000_000_000L, 300_000L, 2L, 10L, ACCOUNT_4, ACCOUNT_GENESIS_1_KEYPAIR);

      final ComparisonResult result = executeAndCompare(Wei.of(1), tx1, tx2, tx3);

      final Optional<BlockAccessList> seqBal = getBlockAccessList(result.seqResult());
      final Optional<BlockAccessList> parBal = getBlockAccessList(result.parResult());
      assertThat(seqBal).isPresent();
      assertThat(parBal).isPresent();

      final List<BlockAccessList.BalanceChange> seqBalChanges =
          getBalanceChangesFor(seqBal.get(), MINING_BENEFICIARY);
      final List<BlockAccessList.BalanceChange> parBalChanges =
          getBalanceChangesFor(parBal.get(), MINING_BENEFICIARY);

      assertThat(seqBalChanges).isNotEmpty();
      assertThat(parBalChanges)
          .as("Parallel BAL mining beneficiary balance changes must match sequential")
          .isEqualTo(seqBalChanges);

      // Each balance change should be strictly increasing (cumulative rewards)
      for (int i = 1; i < seqBalChanges.size(); i++) {
        assertThat(seqBalChanges.get(i).postBalance())
            .as("Balance change at index %d must be >= previous", i)
            .isGreaterThanOrEqualTo(seqBalChanges.get(i - 1).postBalance());
      }
    }
  }

  // ==================== BAL Helpers ====================

  protected List<BalanceChange> getBalanceChangesFor(
      final BlockAccessList bal, final Address address) {
    return bal.accountChanges().stream()
        .filter(ac -> ac.address().equals(address))
        .flatMap(ac -> ac.balanceChanges().stream())
        .toList();
  }
}
