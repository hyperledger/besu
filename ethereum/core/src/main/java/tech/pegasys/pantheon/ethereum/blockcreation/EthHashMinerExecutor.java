package tech.pegasys.pantheon.ethereum.blockcreation;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.PendingTransactions;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashBlockCreator;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolver;
import tech.pegasys.pantheon.ethereum.mainnet.EthHasher;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.Subscribers;

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
