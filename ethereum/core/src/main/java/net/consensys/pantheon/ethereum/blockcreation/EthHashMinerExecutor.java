package net.consensys.pantheon.ethereum.blockcreation;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator.MinedBlockObserver;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.mainnet.EthHashBlockCreator;
import net.consensys.pantheon.ethereum.mainnet.EthHashSolver;
import net.consensys.pantheon.ethereum.mainnet.EthHasher;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.util.Subscribers;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class EthHashMinerExecutor {

  private final ProtocolContext<Void> protocolContext;
  private final ExecutorService executorService;
  private final ProtocolSchedule<Void> protocolSchedule;
  private final PendingTransactions pendingTransactions;
  private volatile BytesValue extraData;
  private volatile Optional<Address> coinbase;
  private volatile Wei minTransactionGasPrice;
  private final BaseBlockScheduler blockScheduler;

  public EthHashMinerExecutor(
      final ProtocolContext<Void> protocolContext,
      final ExecutorService executorService,
      final ProtocolSchedule<Void> protocolSchedule,
      final PendingTransactions pendingTransactions,
      final MiningParameters miningParams,
      final BaseBlockScheduler blockScheduler) {
    this.protocolContext = protocolContext;
    this.executorService = executorService;
    this.protocolSchedule = protocolSchedule;
    this.pendingTransactions = pendingTransactions;
    this.coinbase = miningParams.getCoinbase();
    this.extraData = miningParams.getExtraData();
    this.minTransactionGasPrice = miningParams.getMinTransactionGasPrice();
    this.blockScheduler = blockScheduler;
  }

  public EthHashBlockMiner startAsyncMining(
      final Subscribers<MinedBlockObserver> observers, final BlockHeader parentHeader) {
    if (!coinbase.isPresent()) {
      throw new CoinbaseNotSetException("Unable to start mining without a coinbase.");
    } else {
      final EthHashSolver solver =
          new EthHashSolver(new RandomNonceGenerator(), new EthHasher.Light());
      final EthHashBlockCreator blockCreator =
          new EthHashBlockCreator(
              coinbase.get(),
              parent -> extraData,
              pendingTransactions,
              protocolContext,
              protocolSchedule,
              (gasLimit) -> gasLimit,
              solver,
              minTransactionGasPrice,
              parentHeader);

      final EthHashBlockMiner currentRunningMiner =
          new EthHashBlockMiner(
              blockCreator,
              protocolSchedule,
              protocolContext,
              observers,
              blockScheduler,
              parentHeader);
      executorService.execute(currentRunningMiner);
      return currentRunningMiner;
    }
  }

  public void setExtraData(final BytesValue extraData) {
    this.extraData = extraData.copy();
  }

  public void setCoinbase(final Address coinbase) {
    if (coinbase == null) {
      throw new IllegalArgumentException("Coinbase cannot be unset.");
    } else {
      this.coinbase = Optional.of(coinbase.copy());
    }
  }

  public Optional<Address> getCoinbase() {
    return coinbase;
  }

  public void setMinTransactionGasPrice(final Wei minTransactionGasPrice) {
    this.minTransactionGasPrice = minTransactionGasPrice.copy();
  }

  public Wei getMinTransactionGasPrice() {
    return minTransactionGasPrice;
  }
}
