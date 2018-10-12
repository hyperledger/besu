package tech.pegasys.pantheon.ethereum.blockcreation;

import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent.EventType;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolution;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolverInputs;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.BytesValue;

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
