package net.consensys.pantheon.ethereum.blockcreation;

import net.consensys.pantheon.ethereum.chain.BlockAddedEvent;
import net.consensys.pantheon.ethereum.chain.BlockAddedEvent.EventType;
import net.consensys.pantheon.ethereum.chain.BlockAddedObserver;
import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.mainnet.EthHashSolution;
import net.consensys.pantheon.ethereum.mainnet.EthHashSolverInputs;
import net.consensys.pantheon.util.Subscribers;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

/**
 * Responsible for determining when a block mining operation should be started/stopped, then
 * creating an appropriate miner and starting it running in a thread.
 */
public class MiningCoordinator implements BlockAddedObserver {

  private final Subscribers<MinedBlockObserver> minedBlockObservers = new Subscribers<>();

  private final EthHashMinerExecutor executor;

  private volatile Optional<EthHashBlockMiner> currentRunningMiner = Optional.empty();
  private volatile Optional<Long> cachedHashesPerSecond = Optional.empty();
  private boolean isEnabled = false;
  private final Blockchain blockchain;

  public MiningCoordinator(final Blockchain blockchain, final EthHashMinerExecutor executor) {
    this.executor = executor;
    this.blockchain = blockchain;
    this.blockchain.observeBlockAdded(this);
  }

  public void enable() {
    synchronized (this) {
      if (isEnabled) {
        return;
      }
      startAsyncMiningOperation();
      isEnabled = true;
    }
  }

  public void disable() {
    synchronized (this) {
      if (!isEnabled) {
        return;
      }
      haltCurrentMiningOperation();
      isEnabled = false;
    }
  }

  public boolean isRunning() {
    synchronized (this) {
      return isEnabled;
    }
  }

  public void setCoinbase(final Address coinbase) {
    executor.setCoinbase(coinbase);
  }

  public Optional<Address> getCoinbase() {
    return executor.getCoinbase();
  }

  public void setMinTransactionGasPrice(final Wei minGasPrice) {
    executor.setMinTransactionGasPrice(minGasPrice);
  }

  public Wei getMinTransactionGasPrice() {
    return executor.getMinTransactionGasPrice();
  }

  public void setExtraData(final BytesValue extraData) {
    executor.setExtraData(extraData);
  }

  public Optional<Long> hashesPerSecond() {
    final Optional<Long> currentHashesPerSecond =
        currentRunningMiner.flatMap(EthHashBlockMiner::getHashesPerSecond);

    if (currentHashesPerSecond.isPresent()) {
      cachedHashesPerSecond = currentHashesPerSecond;
      return currentHashesPerSecond;
    } else {
      return cachedHashesPerSecond;
    }
  }

  public Optional<EthHashSolverInputs> getWorkDefinition() {
    return currentRunningMiner.flatMap(EthHashBlockMiner::getWorkDefinition);
  }

  public boolean submitWork(final EthHashSolution solution) {
    synchronized (this) {
      return currentRunningMiner.map(miner -> miner.submitWork(solution)).orElse(false);
    }
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    synchronized (this) {
      if (isEnabled && shouldStartNewMiner(event)) {
        haltCurrentMiningOperation();
        startAsyncMiningOperation();
      }
    }
  }

  private boolean shouldStartNewMiner(final BlockAddedEvent event) {
    return event.getEventType() != EventType.FORK;
  }

  private void startAsyncMiningOperation() {
    final BlockHeader parentHeader = blockchain.getChainHeadHeader();
    currentRunningMiner = Optional.of(executor.startAsyncMining(minedBlockObservers, parentHeader));
  }

  private void haltCurrentMiningOperation() {
    currentRunningMiner.ifPresent(
        miner -> {
          miner.cancel();
          miner.getHashesPerSecond().ifPresent(val -> cachedHashesPerSecond = Optional.of(val));
        });
  }

  public long addMinedBlockObserver(final MinedBlockObserver obs) {
    return minedBlockObservers.subscribe(obs);
  }

  public void removeMinedBlockObserver(final long id) {
    minedBlockObservers.unsubscribe(id);
  }

  public interface MinedBlockObserver {

    void blockMined(Block block);
  }
}
