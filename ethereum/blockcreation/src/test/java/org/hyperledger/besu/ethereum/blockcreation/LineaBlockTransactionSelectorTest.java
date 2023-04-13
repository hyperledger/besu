package org.hyperledger.besu.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.time.ZoneId;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class LineaBlockTransactionSelectorTest extends LondonFeeMarketBlockTransactionSelectorTest {
  private static final int BLOCK_MAX_CALLDATA_SIZE = 1000;
  private static final int TXPOOL_MAX_SIZE = 1000;

  @Override
  protected PendingTransactions createPendingTransactions() {
    return new BaseFeePendingTransactionsSorter(
        ImmutableTransactionPoolConfiguration.builder()
            .txPoolMaxSize(TXPOOL_MAX_SIZE)
            .txPoolLimitByAccountPercentage(1)
            .build(),
        TestClock.system(ZoneId.systemDefault()),
        metricsSystem,
        LineaBlockTransactionSelectorTest::mockBlockHeader);
  }

  private static BlockHeader mockBlockHeader() {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ZERO));
    return blockHeader;
  }

  @Override
  protected FeeMarket getFeeMarket() {
    return FeeMarket.zeroBaseFee(0L);
  }

  @Test
  public void blockCalldataBelowLimitOneTransaction() {
    final ProcessableBlockHeader blockHeader = createBlock(301, Wei.ZERO);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            BLOCK_MAX_CALLDATA_SIZE);

    final Transaction tx = createCalldataTransaction(1, BLOCK_MAX_CALLDATA_SIZE / 10);
    ensureTransactionIsValid(tx);
    pendingTransactions.addRemoteTransaction(tx, Optional.empty());

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions()).hasSize(1);
    assertThat(pendingTransactions.size()).isEqualTo(1);
  }

  @Test
  public void blockCalldataBelowLimitMoreTransactions() {
    final ProcessableBlockHeader blockHeader = createBlock(301, Wei.ZERO);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            BLOCK_MAX_CALLDATA_SIZE);

    final int numTxs = 5;

    for (int i = 0; i < numTxs; i++) {
      final Transaction tx = createCalldataTransaction(i, BLOCK_MAX_CALLDATA_SIZE / (numTxs * 2));
      ensureTransactionIsValid(tx);
      pendingTransactions.addRemoteTransaction(tx, Optional.empty());
    }

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions()).hasSize(numTxs);
    assertThat(pendingTransactions.size()).isEqualTo(numTxs);
  }

  @Test
  public void blockCalldataEqualsLimitMoreTransactions() {
    final ProcessableBlockHeader blockHeader = createBlock(301, Wei.ZERO);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            BLOCK_MAX_CALLDATA_SIZE);

    final int numTxs = 3;
    int currCalldataSize = 0;
    for (int i = 0; i < numTxs; i++) {
      final int txCalldataSize = BLOCK_MAX_CALLDATA_SIZE / (numTxs * 2);
      currCalldataSize += txCalldataSize;
      final Transaction tx = createCalldataTransaction(i, txCalldataSize);
      ensureTransactionIsValid(tx);
      pendingTransactions.addRemoteTransaction(tx, Optional.empty());
    }

    // last tx fill the remaining calldata space for the block
    final Transaction tx =
        createCalldataTransaction(numTxs + 1, BLOCK_MAX_CALLDATA_SIZE - currCalldataSize);
    ensureTransactionIsValid(tx);
    pendingTransactions.addRemoteTransaction(tx, Optional.empty());

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions()).hasSize(numTxs + 1);
    assertThat(pendingTransactions.size()).isEqualTo(numTxs + 1);
  }

  @Test
  public void blockCalldataOverLimitOneTransaction() {
    final ProcessableBlockHeader blockHeader = createBlock(301, Wei.ZERO);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            BLOCK_MAX_CALLDATA_SIZE);

    final Transaction tx = createCalldataTransaction(1, BLOCK_MAX_CALLDATA_SIZE + 1);
    ensureTransactionIsValid(tx);
    pendingTransactions.addRemoteTransaction(tx, Optional.empty());

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions()).hasSize(0);
    assertThat(pendingTransactions.size()).isEqualTo(1);
  }

  @Test
  public void blockCalldataOverLimitAfterSomeTransactions() {
    final ProcessableBlockHeader blockHeader = createBlock(301, Wei.ZERO);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            BLOCK_MAX_CALLDATA_SIZE);

    final int numTxs = 5;
    for (int i = 0; i < numTxs; i++) {
      final int txCalldataSize = BLOCK_MAX_CALLDATA_SIZE / (numTxs - 1);
      final Transaction tx = createCalldataTransaction(i, txCalldataSize);
      ensureTransactionIsValid(tx);
      pendingTransactions.addRemoteTransaction(tx, Optional.empty());
    }

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions())
        .map(Transaction::getNonce)
        .containsExactlyInAnyOrder(0L, 1L, 2L, 3L);
    assertThat(pendingTransactions.size()).isEqualTo(numTxs);
  }

  private Transaction createCalldataTransaction(
      final int transactionNumber, final int payloadSize) {
    return createCalldataTransaction(transactionNumber, Bytes.random(payloadSize));
  }

  private Transaction createCalldataTransaction(final int transactionNumber, final Bytes payload) {
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .gasLimit(10)
        .maxFeePerGas(Wei.ZERO)
        .maxPriorityFeePerGas(Wei.ZERO)
        .nonce(transactionNumber)
        .payload(payload)
        .to(Address.ID)
        .value(Wei.of(transactionNumber))
        .sender(Address.ID)
        .chainId(BigInteger.ONE)
        .guessType()
        .signAndBuild(keyPair);
  }
}
