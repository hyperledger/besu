package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Optional;
import java.util.stream.Stream;

public class TransitionProtocolSchedule extends TransitionUtils<ProtocolSchedule>
    implements ProtocolSchedule {

  public TransitionProtocolSchedule(
      final ProtocolSchedule preMergeProtocolSchedule,
      final ProtocolSchedule postMergeProtocolSchedule) {
    super(preMergeProtocolSchedule, postMergeProtocolSchedule);
  }

  @Override
  public ProtocolSpec getByBlockNumber(final long number) {
    return dispatchFunctionAccordingToMergeState(
        protocolSchedule -> protocolSchedule.getByBlockNumber(number));
  }

  @Override
  public Stream<Long> streamMilestoneBlocks() {
    return dispatchFunctionAccordingToMergeState(ProtocolSchedule::streamMilestoneBlocks);
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return dispatchFunctionAccordingToMergeState(ProtocolSchedule::getChainId);
  }

  @Override
  public void setTransactionFilter(final TransactionFilter transactionFilter) {
    dispatchConsumerAccordingToMergeState(
        protocolSchedule -> protocolSchedule.setTransactionFilter(transactionFilter));
  }

  @Override
  public void setPublicWorldStateArchiveForPrivacyBlockProcessor(
      final WorldStateArchive publicWorldStateArchive) {
    dispatchConsumerAccordingToMergeState(
        protocolSchedule ->
            protocolSchedule.setPublicWorldStateArchiveForPrivacyBlockProcessor(
                publicWorldStateArchive));
  }
}
