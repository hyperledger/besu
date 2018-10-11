package net.consensys.pantheon.consensus.clique.blockcreation;

import net.consensys.pantheon.consensus.clique.CliqueContext;
import net.consensys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import net.consensys.pantheon.ethereum.chain.Blockchain;

import java.util.Optional;

public class CliqueMiningCoordinator
    extends AbstractMiningCoordinator<CliqueContext, CliqueBlockMiner> {

  public CliqueMiningCoordinator(final Blockchain blockchain, final CliqueMinerExecutor executor) {
    super(blockchain, executor);
  }

  @Override
  protected void haltCurrentMiningOperation() {
    currentRunningMiner.ifPresent(CliqueBlockMiner::cancel);
    currentRunningMiner = Optional.empty();
  }
}
