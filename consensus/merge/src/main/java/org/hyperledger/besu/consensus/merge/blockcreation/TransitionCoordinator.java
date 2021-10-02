package org.hyperledger.besu.consensus.merge.blockcreation;

import org.hyperledger.besu.consensus.merge.TransitionUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class TransitionCoordinator extends TransitionUtils<MiningCoordinator>
    implements MiningCoordinator {

  private final MiningCoordinator miningCoordinator;
  private final MiningCoordinator mergeCoordinator;

  public TransitionCoordinator(
      final MiningCoordinator miningCoordinator, final MiningCoordinator mergeCoordinator) {
    super(miningCoordinator, mergeCoordinator);
    this.miningCoordinator = miningCoordinator;
    this.mergeCoordinator = mergeCoordinator;
  }

  @Override
  public void start() {
    miningCoordinator.start();
  }

  @Override
  public void stop() {
    miningCoordinator.stop();
  }

  @Override
  public void awaitStop() throws InterruptedException {
    miningCoordinator.awaitStop();
  }

  @Override
  public boolean enable() {
    return dispatchFunctionAccordingToMergeState(MiningCoordinator::enable);
  }

  @Override
  public boolean disable() {
    return dispatchFunctionAccordingToMergeState(MiningCoordinator::disable);
  }

  @Override
  public boolean isMining() {
    return dispatchFunctionAccordingToMergeState(MiningCoordinator::isMining);
  }

  @Override
  public Wei getMinTransactionGasPrice() {
    return dispatchFunctionAccordingToMergeState(MiningCoordinator::getMinTransactionGasPrice);
  }

  @Override
  public void setExtraData(final Bytes extraData) {
    // todo check if this makes sense to do for both
    miningCoordinator.setExtraData(extraData);
    mergeCoordinator.setExtraData(extraData);
  }

  @Override
  public Optional<Address> getCoinbase() {
    return dispatchFunctionAccordingToMergeState(MiningCoordinator::getCoinbase);
  }

  @Override
  public Optional<Block> createBlock(
      final BlockHeader parentHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {
    return dispatchFunctionAccordingToMergeState(
        (MiningCoordinator coordinator) ->
            miningCoordinator.createBlock(parentHeader, transactions, ommers));
  }

  @Override
  public Optional<Block> createBlock(final BlockHeader parentHeader, final long timestamp) {
    return dispatchFunctionAccordingToMergeState(
        (MiningCoordinator coordinator) -> coordinator.createBlock(parentHeader, timestamp));
  }

  @Override
  public void changeTargetGasLimit(final Long targetGasLimit) {
    // todo check if this makes sense to do for both
    miningCoordinator.changeTargetGasLimit(targetGasLimit);
    mergeCoordinator.changeTargetGasLimit(targetGasLimit);
  }
}
