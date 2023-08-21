/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.blockcreation;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs.DEFAULT_DEPOSIT_CONTRACT_ADDRESS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.BLSSignature;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator.BlockCreationResult;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlobTestFixture;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionBroadcaster;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.DepositsValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsProcessor;
import org.hyperledger.besu.ethereum.mainnet.feemarket.CancunFeeMarket;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
abstract class AbstractBlockCreatorTest {
  private static final Optional<Address> EMPTY_DEPOSIT_CONTRACT_ADDRESS = Optional.empty();
  @Mock private WithdrawalsProcessor withdrawalsProcessor;

  @Test
  void findDepositsFromReceipts() {
    final AbstractBlockCreator blockCreator =
        blockCreatorWithAllowedDeposits(Optional.of(DEFAULT_DEPOSIT_CONTRACT_ADDRESS));
    final BlockTransactionSelector.TransactionSelectionResults transactionResults =
        mock(BlockTransactionSelector.TransactionSelectionResults.class);
    BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
    TransactionReceipt receiptWithoutDeposit1 = blockDataGenerator.receipt();
    TransactionReceipt receiptWithoutDeposit2 = blockDataGenerator.receipt();
    final Log depositLog =
        new Log(
            DEFAULT_DEPOSIT_CONTRACT_ADDRESS,
            Bytes.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000018000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000030b10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200017a7fcf06faf493d30bbe2632ea7c2383cd86825e12797165de7aa35589483000000000000000000000000000000000000000000000000000000000000000800405973070000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000060a889db8300194050a2636c92a95bc7160515867614b7971a9500cdb62f9c0890217d2901c3241f86fac029428fc106930606154bd9e406d7588934a5f15b837180b17194d6e44bd6de23e43b163dfe12e369dcc75a3852cd997963f158217eb500000000000000000000000000000000000000000000000000000000000000083f3d080000000000000000000000000000000000000000000000000000000000"),
            List.of(
                LogTopic.fromHexString(
                    "0x649bbc62d0e31342afea4e5cd82d4049e7e1ee912fc0889aa790803be39038c5")));
    final TransactionReceipt receiptWithDeposit = blockDataGenerator.receipt(List.of(depositLog));
    when(transactionResults.getReceipts())
        .thenReturn(List.of(receiptWithoutDeposit1, receiptWithDeposit, receiptWithoutDeposit2));

    Deposit expectedDeposit =
        new Deposit(
            BLSPublicKey.fromHexString(
                "0xb10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e"),
            Bytes32.fromHexString(
                "0x0017a7fcf06faf493d30bbe2632ea7c2383cd86825e12797165de7aa35589483"),
            GWei.of(32000000000L),
            BLSSignature.fromHexString(
                "0xa889db8300194050a2636c92a95bc7160515867614b7971a9500cdb62f9c0890217d2901c3241f86fac029428fc106930606154bd9e406d7588934a5f15b837180b17194d6e44bd6de23e43b163dfe12e369dcc75a3852cd997963f158217eb5"),
            UInt64.valueOf(539967));
    final List<Deposit> expectedDeposits = List.of(expectedDeposit);

    final List<Deposit> depositsFromReceipts =
        blockCreator.findDepositsFromReceipts(transactionResults);

    assertThat(depositsFromReceipts).isEqualTo(expectedDeposits);
  }

  @Test
  void withAllowedDepositsAndContractAddress_DepositsAreParsed() {
    final AbstractBlockCreator blockCreator =
        blockCreatorWithAllowedDeposits(Optional.of(DEFAULT_DEPOSIT_CONTRACT_ADDRESS));

    final BlockCreationResult blockCreationResult =
        blockCreator.createBlock(
            Optional.empty(),
            Optional.empty(),
            Optional.of(emptyList()),
            Optional.empty(),
            1L,
            false);

    List<Deposit> deposits = emptyList();
    final Hash depositsRoot = BodyValidation.depositsRoot(deposits);
    assertThat(blockCreationResult.getBlock().getHeader().getDepositsRoot()).hasValue(depositsRoot);
    assertThat(blockCreationResult.getBlock().getBody().getDeposits()).hasValue(deposits);
  }

  @Test
  void withAllowedDepositsAndNoContractAddress_DepositsAreNotParsed() {
    final AbstractBlockCreator blockCreator = blockCreatorWithAllowedDeposits(Optional.empty());

    final BlockCreationResult blockCreationResult =
        blockCreator.createBlock(
            Optional.empty(),
            Optional.empty(),
            Optional.of(emptyList()),
            Optional.empty(),
            1L,
            false);

    assertThat(blockCreationResult.getBlock().getHeader().getDepositsRoot()).isEmpty();
    assertThat(blockCreationResult.getBlock().getBody().getDeposits()).isEmpty();
  }

  @Test
  void withProhibitedDeposits_DepositsAreNotParsed() {
    final AbstractBlockCreator blockCreator = blockCreatorWithProhibitedDeposits();

    final BlockCreationResult blockCreationResult =
        blockCreator.createBlock(
            Optional.empty(),
            Optional.empty(),
            Optional.of(emptyList()),
            Optional.empty(),
            1L,
            false);

    assertThat(blockCreationResult.getBlock().getHeader().getDepositsRoot()).isEmpty();
    assertThat(blockCreationResult.getBlock().getBody().getDeposits()).isEmpty();
  }

  private AbstractBlockCreator blockCreatorWithAllowedDeposits(
      final Optional<Address> depositContractAddress) {
    final ProtocolSpecAdapters protocolSpecAdapters =
        ProtocolSpecAdapters.create(
            0,
            specBuilder ->
                specBuilder.depositsValidator(
                    new DepositsValidator.AllowedDeposits(depositContractAddress.orElse(null))));
    return createBlockCreator(protocolSpecAdapters, depositContractAddress);
  }

  private AbstractBlockCreator blockCreatorWithProhibitedDeposits() {
    final ProtocolSpecAdapters protocolSpecAdapters =
        ProtocolSpecAdapters.create(
            0,
            specBuilder ->
                specBuilder.depositsValidator(new DepositsValidator.ProhibitedDeposits()));
    return createBlockCreator(protocolSpecAdapters, Optional.of(DEFAULT_DEPOSIT_CONTRACT_ADDRESS));
  }

  @Test
  void withProcessorAndEmptyWithdrawals_NoWithdrawalsAreProcessed() {
    final AbstractBlockCreator blockCreator = blockCreatorWithWithdrawalsProcessor();
    final BlockCreationResult blockCreationResult =
        blockCreator.createBlock(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1L, false);
    verify(withdrawalsProcessor, never()).processWithdrawals(any(), any());
    assertThat(blockCreationResult.getBlock().getHeader().getWithdrawalsRoot()).isEmpty();
    assertThat(blockCreationResult.getBlock().getBody().getWithdrawals()).isEmpty();
  }

  @Test
  void withNoProcessorAndEmptyWithdrawals_NoWithdrawalsAreNotProcessed() {
    final AbstractBlockCreator blockCreator = blockCreatorWithoutWithdrawalsProcessor();
    final BlockCreationResult blockCreationResult =
        blockCreator.createBlock(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1L, false);
    verify(withdrawalsProcessor, never()).processWithdrawals(any(), any());
    assertThat(blockCreationResult.getBlock().getHeader().getWithdrawalsRoot()).isEmpty();
    assertThat(blockCreationResult.getBlock().getBody().getWithdrawals()).isEmpty();
  }

  @Test
  void withProcessorAndWithdrawals_WithdrawalsAreProcessed() {
    final AbstractBlockCreator blockCreator = blockCreatorWithWithdrawalsProcessor();
    final List<Withdrawal> withdrawals =
        List.of(new Withdrawal(UInt64.ONE, UInt64.ONE, Address.fromHexString("0x1"), GWei.ONE));
    final BlockCreationResult blockCreationResult =
        blockCreator.createBlock(
            Optional.empty(),
            Optional.empty(),
            Optional.of(withdrawals),
            Optional.empty(),
            1L,
            false);

    final Hash withdrawalsRoot = BodyValidation.withdrawalsRoot(withdrawals);
    verify(withdrawalsProcessor).processWithdrawals(eq(withdrawals), any());
    assertThat(blockCreationResult.getBlock().getHeader().getWithdrawalsRoot())
        .hasValue(withdrawalsRoot);
    assertThat(blockCreationResult.getBlock().getBody().getWithdrawals()).hasValue(withdrawals);
  }

  @Test
  void withNoProcessorAndWithdrawals_WithdrawalsAreNotProcessed() {
    final AbstractBlockCreator blockCreator = blockCreatorWithoutWithdrawalsProcessor();
    final List<Withdrawal> withdrawals =
        List.of(new Withdrawal(UInt64.ONE, UInt64.ONE, Address.fromHexString("0x1"), GWei.ONE));
    final BlockCreationResult blockCreationResult =
        blockCreator.createBlock(
            Optional.empty(),
            Optional.empty(),
            Optional.of(withdrawals),
            Optional.empty(),
            1L,
            false);
    verify(withdrawalsProcessor, never()).processWithdrawals(any(), any());
    assertThat(blockCreationResult.getBlock().getHeader().getWithdrawalsRoot()).isEmpty();
    assertThat(blockCreationResult.getBlock().getBody().getWithdrawals()).isEmpty();
  }

  @Disabled
  @Test
  public void computesGasUsageFromIncludedTransactions() {
    final KeyPair senderKeys = SignatureAlgorithmFactory.getInstance().generateKeyPair();
    final AbstractBlockCreator blockCreator = blockCreatorWithBlobGasSupport();
    BlobTestFixture blobTestFixture = new BlobTestFixture();
    BlobsWithCommitments bwc = blobTestFixture.createBlobsWithCommitments(6);
    TransactionTestFixture ttf = new TransactionTestFixture();
    Transaction fullOfBlobs =
        ttf.to(Optional.of(Address.ZERO))
            .type(TransactionType.BLOB)
            .chainId(Optional.of(BigInteger.valueOf(42)))
            .maxFeePerGas(Optional.of(Wei.of(15)))
            .maxFeePerBlobGas(Optional.of(Wei.of(128)))
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .versionedHashes(Optional.of(bwc.getVersionedHashes()))
            .createTransaction(senderKeys);

    ttf.blobsWithCommitments(Optional.of(bwc));
    final BlockCreationResult blockCreationResult =
        blockCreator.createBlock(
            Optional.of(List.of(fullOfBlobs)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            1L,
            false);
    long blobGasUsage = blockCreationResult.getBlock().getHeader().getGasUsed();
    assertThat(blobGasUsage).isNotZero();
    BlobGas excessBlobGas = blockCreationResult.getBlock().getHeader().getExcessBlobGas().get();
    assertThat(excessBlobGas).isNotNull();
  }

  private AbstractBlockCreator blockCreatorWithBlobGasSupport() {
    final ProtocolSpecAdapters protocolSpecAdapters =
        ProtocolSpecAdapters.create(
            0,
            specBuilder -> {
              specBuilder.feeMarket(new CancunFeeMarket(0, Optional.empty()));
              specBuilder.isReplayProtectionSupported(true);
              specBuilder.withdrawalsProcessor(withdrawalsProcessor);
              return specBuilder;
            });
    return createBlockCreator(protocolSpecAdapters, EMPTY_DEPOSIT_CONTRACT_ADDRESS);
  }

  private AbstractBlockCreator blockCreatorWithWithdrawalsProcessor() {
    final ProtocolSpecAdapters protocolSpecAdapters =
        ProtocolSpecAdapters.create(
            0, specBuilder -> specBuilder.withdrawalsProcessor(withdrawalsProcessor));
    return createBlockCreator(protocolSpecAdapters, EMPTY_DEPOSIT_CONTRACT_ADDRESS);
  }

  private AbstractBlockCreator blockCreatorWithoutWithdrawalsProcessor() {
    return createBlockCreator(new ProtocolSpecAdapters(Map.of()), EMPTY_DEPOSIT_CONTRACT_ADDRESS);
  }

  private AbstractBlockCreator createBlockCreator(
      final ProtocolSpecAdapters protocolSpecAdapters,
      final Optional<Address> depositContractAddress) {
    final GenesisConfigOptions genesisConfigOptions = GenesisConfigFile.DEFAULT.getConfigOptions();
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder()
            .protocolSchedule(
                new ProtocolScheduleBuilder(
                        genesisConfigOptions,
                        BigInteger.valueOf(42),
                        protocolSpecAdapters,
                        PrivacyParameters.DEFAULT,
                        false,
                        EvmConfiguration.DEFAULT)
                    .createProtocolSchedule())
            .build();

    final MutableBlockchain blockchain = executionContextTestFixture.getBlockchain();
    final TransactionPoolConfiguration poolConf =
        ImmutableTransactionPoolConfiguration.builder().txPoolMaxSize(100).build();
    final AbstractPendingTransactionsSorter sorter =
        new GasPricePendingTransactionsSorter(
            poolConf, Clock.systemUTC(), new NoOpMetricsSystem(), blockchain::getChainHeadHeader);

    final EthContext ethContext = mock(EthContext.class, RETURNS_DEEP_STUBS);
    when(ethContext.getEthPeers().subscribeConnect(any())).thenReturn(1L);

    final TransactionPool transactionPool =
        new TransactionPool(
            () -> sorter,
            executionContextTestFixture.getProtocolSchedule(),
            executionContextTestFixture.getProtocolContext(),
            mock(TransactionBroadcaster.class),
            ethContext,
            mock(MiningParameters.class),
            new TransactionPoolMetrics(new NoOpMetricsSystem()),
            poolConf);
    transactionPool.setEnabled();

    return new TestBlockCreator(
        Address.ZERO,
        __ -> Address.ZERO,
        () -> Optional.of(30_000_000L),
        __ -> Bytes.fromHexString("deadbeef"),
        transactionPool,
        executionContextTestFixture.getProtocolContext(),
        executionContextTestFixture.getProtocolSchedule(),
        Wei.of(1L),
        0d,
        blockchain.getChainHeadHeader(),
        depositContractAddress);
  }

  static class TestBlockCreator extends AbstractBlockCreator {

    protected TestBlockCreator(
        final Address coinbase,
        final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        final Supplier<Optional<Long>> targetGasLimitSupplier,
        final ExtraDataCalculator extraDataCalculator,
        final TransactionPool transactionPool,
        final ProtocolContext protocolContext,
        final ProtocolSchedule protocolSchedule,
        final Wei minTransactionGasPrice,
        final Double minBlockOccupancyRatio,
        final BlockHeader parentHeader,
        final Optional<Address> depositContractAddress) {
      super(
          coinbase,
          miningBeneficiaryCalculator,
          targetGasLimitSupplier,
          extraDataCalculator,
          transactionPool,
          protocolContext,
          protocolSchedule,
          minTransactionGasPrice,
          minBlockOccupancyRatio,
          parentHeader,
          depositContractAddress);
    }

    @Override
    protected BlockHeader createFinalBlockHeader(final SealableBlockHeader sealableBlockHeader) {
      return BlockHeaderBuilder.create()
          .difficulty(Difficulty.ZERO)
          .populateFrom(sealableBlockHeader)
          .mixHash(Hash.EMPTY)
          .nonce(0L)
          .blockHeaderFunctions(blockHeaderFunctions)
          .buildBlockHeader();
    }
  }
}
