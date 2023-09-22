package org.hyperledger.besu.ethereum.blockcreation.evaluation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public final class BlockSelectionContext {
  private final GasCalculator gasCalculator;
  private final GasLimitCalculator gasLimitCalculator;
  private final Wei minTransactionGasPrice;
  private final Double minBlockOccupancyRatio;
  private final ProcessableBlockHeader processableBlockHeader;
  private final FeeMarket feeMarket;
  private final Wei blobGasPrice;
  private final Address miningBeneficiary;
  private final TransactionPool transactionPool;

  private final TransactionSelectionResults transactionSelectionResults;

  public BlockSelectionContext(
          final GasCalculator gasCalculator,
          final GasLimitCalculator gasLimitCalculator,
          final Wei minTransactionGasPrice,
          final Double minBlockOccupancyRatio,
          final ProcessableBlockHeader processableBlockHeader,
          final FeeMarket feeMarket,
          final Wei blobGasPrice,
          final Address miningBeneficiary,
          final TransactionPool transactionPool, TransactionSelectionResults transactionSelectionResults) {
    this.gasCalculator = gasCalculator;
    this.gasLimitCalculator = gasLimitCalculator;
    this.minTransactionGasPrice = minTransactionGasPrice;
    this.minBlockOccupancyRatio = minBlockOccupancyRatio;
    this.processableBlockHeader = processableBlockHeader;
    this.feeMarket = feeMarket;
    this.blobGasPrice = blobGasPrice;
    this.miningBeneficiary = miningBeneficiary;
    this.transactionPool = transactionPool;
    this.transactionSelectionResults = transactionSelectionResults;
  }

  public GasCalculator gasCalculator() {
    return gasCalculator;
  }

  public GasLimitCalculator gasLimitCalculator() {
    return gasLimitCalculator;
  }

  public Wei minTransactionGasPrice() {
    return minTransactionGasPrice;
  }

  public Double minBlockOccupancyRatio() {
    return minBlockOccupancyRatio;
  }

  public ProcessableBlockHeader processableBlockHeader() {
    return processableBlockHeader;
  }

  public FeeMarket feeMarket() {
    return feeMarket;
  }

  public Wei blobGasPrice() {
    return blobGasPrice;
  }

  public Address miningBeneficiary() {
    return miningBeneficiary;
  }

  public TransactionPool transactionPool() {
    return transactionPool;
  }
  public TransactionSelectionResults getTransactionSelectionResults() {
    return transactionSelectionResults;
  }
}
