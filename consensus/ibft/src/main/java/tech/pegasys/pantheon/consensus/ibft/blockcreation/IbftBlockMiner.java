package tech.pegasys.pantheon.consensus.ibft.blockcreation;

import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockScheduler;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.blockcreation.BlockMiner;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.Subscribers;

public class IbftBlockMiner extends BlockMiner<IbftContext, IbftBlockCreator> {

  // TODO(tmm): Currently a place holder to allow infrastructure code to continue to operate
  // with the advent of multiple consensus methods.
  public IbftBlockMiner(
      final IbftBlockCreator blockCreator,
      final ProtocolSchedule<IbftContext> protocolSchedule,
      final ProtocolContext<IbftContext> protocolContext,
      final Subscribers<MinedBlockObserver> observers,
      final AbstractBlockScheduler scheduler,
      final BlockHeader parentHeader) {
    super(blockCreator, protocolSchedule, protocolContext, observers, scheduler, parentHeader);
  }
}
