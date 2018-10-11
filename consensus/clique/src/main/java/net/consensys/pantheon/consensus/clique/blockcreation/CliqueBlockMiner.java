package net.consensys.pantheon.consensus.clique.blockcreation;

import net.consensys.pantheon.consensus.clique.CliqueContext;
import net.consensys.pantheon.consensus.clique.CliqueHelpers;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.AbstractBlockScheduler;
import net.consensys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator.MinedBlockObserver;
import net.consensys.pantheon.ethereum.blockcreation.BlockMiner;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.util.Subscribers;

public class CliqueBlockMiner extends BlockMiner<CliqueContext, CliqueBlockCreator> {

  private final Address localAddress;

  public CliqueBlockMiner(
      final CliqueBlockCreator blockCreator,
      final ProtocolSchedule<CliqueContext> protocolSchedule,
      final ProtocolContext<CliqueContext> protocolContext,
      final Subscribers<MinedBlockObserver> observers,
      final AbstractBlockScheduler scheduler,
      final BlockHeader parentHeader,
      final Address localAddress) {
    super(blockCreator, protocolSchedule, protocolContext, observers, scheduler, parentHeader);
    this.localAddress = localAddress;
  }

  @Override
  protected boolean mineBlock() throws InterruptedException {
    if (CliqueHelpers.addressIsAllowedToProduceNextBlock(
        localAddress, protocolContext, parentHeader)) {
      return super.mineBlock();
    }

    return true; // terminate mining.
  }
}
