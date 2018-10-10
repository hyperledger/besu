package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.MutableAccount;
import net.consensys.pantheon.ethereum.core.MutableWorldState;
import net.consensys.pantheon.ethereum.core.ProcessableBlockHeader;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.TransactionReceipt;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.core.WorldState;
import net.consensys.pantheon.ethereum.core.WorldUpdater;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainnetBlockProcessor implements BlockProcessor {

  @FunctionalInterface
  public interface TransactionReceiptFactory {

    TransactionReceipt create(
        TransactionProcessor.Result result, WorldState worldState, long gasUsed);
  }

  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_GENERATION = 6;

  public static class Result implements BlockProcessor.Result {

    private static final Result FAILED = new Result(false, null);

    private final boolean successful;

    private final List<TransactionReceipt> receipts;

    public static Result successful(final List<TransactionReceipt> receipts) {
      return new Result(true, ImmutableList.copyOf(receipts));
    }

    public static Result failed() {
      return FAILED;
    }

    Result(final boolean successful, final List<TransactionReceipt> receipts) {
      this.successful = successful;
      this.receipts = receipts;
    }

    @Override
    public List<TransactionReceipt> getReceipts() {
      return receipts;
    }

    @Override
    public boolean isSuccessful() {
      return successful;
    }
  }

  private final TransactionProcessor transactionProcessor;

  private final TransactionReceiptFactory transactionReceiptFactory;

  private final Wei blockReward;

  private final MiningBeneficiaryCalculator miningBeneficiaryCalculator;

  public MainnetBlockProcessor(
      final TransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator) {
    this.transactionProcessor = transactionProcessor;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.blockReward = blockReward;
    this.miningBeneficiaryCalculator = miningBeneficiaryCalculator;
  }

  @Override
  public Result processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {

    long gasUsed = 0;
    final List<TransactionReceipt> receipts = new ArrayList<>();

    for (int i = 0; i < transactions.size(); ++i) {
      final Transaction transaction = transactions.get(i);

      final long remainingGasBudget = blockHeader.getGasLimit() - gasUsed;
      if (Long.compareUnsigned(transaction.getGasLimit(), remainingGasBudget) > 0) {
        LOG.warn(
            "Transaction processing error: transaction gas limit {} exceeds available block budget remaining {}",
            transaction.getGasLimit(),
            remainingGasBudget);
        return Result.failed();
      }

      final WorldUpdater worldStateUpdater = worldState.updater();
      final Address miningBeneficiary =
          miningBeneficiaryCalculator.calculateBeneficiary(blockHeader);
      final TransactionProcessor.Result result =
          transactionProcessor.processTransaction(
              blockchain, worldStateUpdater, blockHeader, transaction, miningBeneficiary);
      if (result.isInvalid()) {
        return Result.failed();
      }

      worldStateUpdater.commit();
      gasUsed = transaction.getGasLimit() - result.getGasRemaining() + gasUsed;
      final TransactionReceipt transactionReceipt =
          transactionReceiptFactory.create(result, worldState, gasUsed);
      receipts.add(transactionReceipt);
    }

    if (!rewardCoinbase(worldState, blockHeader, ommers)) {
      return Result.failed();
    }

    worldState.persist();
    return Result.successful(receipts);
  }

  protected Address calculateFeeRecipient(final BlockHeader header) {
    return header.getCoinbase();
  }

  private boolean rewardCoinbase(
      final MutableWorldState worldState,
      final ProcessableBlockHeader header,
      final List<BlockHeader> ommers) {
    if (blockReward.isZero()) {
      return true;
    }
    final Wei coinbaseReward = blockReward.plus(blockReward.times(ommers.size()).dividedBy(32));
    final WorldUpdater updater = worldState.updater();
    final MutableAccount coinbase = updater.getOrCreate(header.getCoinbase());

    coinbase.incrementBalance(coinbaseReward);
    for (final BlockHeader ommerHeader : ommers) {
      if (ommerHeader.getNumber() - header.getNumber() > MAX_GENERATION) {
        LOG.warn(
            "Block processing error: ommer block number {} more than {} generations current block number {}",
            ommerHeader.getNumber(),
            MAX_GENERATION,
            header.getNumber());
        return false;
      }

      final MutableAccount ommerCoinbase = updater.getOrCreate(ommerHeader.getCoinbase());
      final long distance = header.getNumber() - ommerHeader.getNumber();
      final Wei ommerReward = blockReward.minus(blockReward.times(distance).dividedBy(8));
      ommerCoinbase.incrementBalance(ommerReward);
    }

    updater.commit();

    return true;
  }
}
