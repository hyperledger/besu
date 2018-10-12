package net.consensys.pantheon.consensus.ibft.blockcreation;

import net.consensys.pantheon.consensus.ibft.IbftContext;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.AbstractBlockScheduler;
import net.consensys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator.MinedBlockObserver;
import net.consensys.pantheon.ethereum.blockcreation.BlockMiner;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.util.Subscribers;

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
