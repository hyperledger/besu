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

public abstract class AbstractMiningCoordinator<
        C, M extends BlockMiner<C, ? extends AbstractBlockCreator<C>>>
    implements BlockAddedObserver {

  protected boolean isEnabled = false;
  protected volatile Optional<M> currentRunningMiner = Optional.empty();

  private final Subscribers<MinedBlockObserver> minedBlockObservers = new Subscribers<>();
  private final AbstractMinerExecutor<C, M> executor;
  protected final Blockchain blockchain;

  public AbstractMiningCoordinator(
      final Blockchain blockchain, final AbstractMinerExecutor<C, M> executor) {
    this.executor = executor;
    this.blockchain = blockchain;
    this.blockchain.observeBlockAdded(this);
  }

  public abstract void enable();

  public abstract void disable();

  public boolean isRunning() {
    synchronized (this) {
      return isEnabled;
    }
  }

  protected void startAsyncMiningOperation() {
    final BlockHeader parentHeader = blockchain.getChainHeadHeader();
    currentRunningMiner = Optional.of(executor.startAsyncMining(minedBlockObservers, parentHeader));
  }

  protected void haltCurrentMiningOperation() {
    currentRunningMiner.ifPresent(M::cancel);
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

  public void removeMinedBlockObserver(final long id) {
    minedBlockObservers.unsubscribe(id);
  }

  public long addMinedBlockObserver(final MinedBlockObserver obs) {
    return minedBlockObservers.subscribe(obs);
  }

  // Required for JSON RPC, and are deemed to be valid for all mining mechanisms
  public void setMinTransactionGasPrice(final Wei minGasPrice) {
    executor.setMinTransactionGasPrice(minGasPrice);
  }

  public Wei getMinTransactionGasPrice() {
    return executor.getMinTransactionGasPrice();
  }

  public void setExtraData(final BytesValue extraData) {
    executor.setExtraData(extraData);
  }

  public void setCoinbase(final Address coinbase) {
    throw new UnsupportedOperationException(
        "Current consensus mechanism prevents" + " setting coinbase.");
  }

  public Optional<Address> getCoinbase() {
    throw new UnsupportedOperationException(
        "Current consensus mechanism prevents" + " querying of coinbase.");
  }

  public Optional<Long> hashesPerSecond() {
    throw new UnsupportedOperationException(
        "Current consensus mechanism prevents querying " + "of hashrate.");
  }

  public Optional<EthHashSolverInputs> getWorkDefinition() {
    throw new UnsupportedOperationException(
        "Current consensus mechanism prevents querying " + "work definition.");
  }

  public boolean submitWork(final EthHashSolution solution) {
    throw new UnsupportedOperationException(
        "Current consensus mechanism prevents submission of work" + " solutions.");
  }

  public interface MinedBlockObserver {

    void blockMined(Block block);
  }
}
