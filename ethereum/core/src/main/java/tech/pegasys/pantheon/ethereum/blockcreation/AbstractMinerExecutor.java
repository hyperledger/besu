package tech.pegasys.pantheon.ethereum.blockcreation;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.PendingTransactions;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.concurrent.ExecutorService;

public abstract class AbstractMinerExecutor<
    C, M extends BlockMiner<C, ? extends AbstractBlockCreator<C>>> {

  protected final ProtocolContext<C> protocolContext;
  protected final ExecutorService executorService;
  protected final ProtocolSchedule<C> protocolSchedule;
  protected final PendingTransactions pendingTransactions;
  protected final AbstractBlockScheduler blockScheduler;

  protected volatile BytesValue extraData;
  protected volatile Wei minTransactionGasPrice;

  public AbstractMinerExecutor(
      final ProtocolContext<C> protocolContext,
      final ExecutorService executorService,
      final ProtocolSchedule<C> protocolSchedule,
      final PendingTransactions pendingTransactions,
      final MiningParameters miningParams,
      final AbstractBlockScheduler blockScheduler) {
    this.protocolContext = protocolContext;
    this.executorService = executorService;
    this.protocolSchedule = protocolSchedule;
    this.pendingTransactions = pendingTransactions;
    this.extraData = miningParams.getExtraData();
    this.minTransactionGasPrice = miningParams.getMinTransactionGasPrice();
    this.blockScheduler = blockScheduler;
  }

  public abstract M startAsyncMining(
      final Subscribers<MinedBlockObserver> observers, final BlockHeader parentHeader);

  public void setExtraData(final BytesValue extraData) {
    this.extraData = extraData.copy();
  }

  public void setMinTransactionGasPrice(final Wei minTransactionGasPrice) {
    this.minTransactionGasPrice = minTransactionGasPrice.copy();
  }

  public Wei getMinTransactionGasPrice() {
    return minTransactionGasPrice;
  }
}
