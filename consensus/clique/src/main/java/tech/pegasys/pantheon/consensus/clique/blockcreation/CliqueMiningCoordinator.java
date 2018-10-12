package tech.pegasys.pantheon.consensus.clique.blockcreation;

import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;

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
