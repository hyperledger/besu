package net.consensys.pantheon.ethereum.blockcreation;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator.MinedBlockObserver;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.mainnet.EthHashBlockCreator;
import net.consensys.pantheon.ethereum.mainnet.EthHashSolver;
import net.consensys.pantheon.ethereum.mainnet.EthHasher;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.util.Subscribers;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class EthHashMinerExecutor extends AbstractMinerExecutor<Void, EthHashBlockMiner> {

  private volatile Optional<Address> coinbase;

  public EthHashMinerExecutor(
      final ProtocolContext<Void> protocolContext,
      final ExecutorService executorService,
      final ProtocolSchedule<Void> protocolSchedule,
      final PendingTransactions pendingTransactions,
      final MiningParameters miningParams,
      final AbstractBlockScheduler blockScheduler) {
    super(
        protocolContext,
        executorService,
        protocolSchedule,
        pendingTransactions,
        miningParams,
        blockScheduler);
    this.coinbase = miningParams.getCoinbase();
  }

  @Override
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
}
