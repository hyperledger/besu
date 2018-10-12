package tech.pegasys.pantheon.consensus.clique.blockcreation;

import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueHelpers;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockScheduler;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.blockcreation.BlockMiner;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.Subscribers;

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
