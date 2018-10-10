package net.consensys.pantheon.ethereum.blockcreation;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator.MinedBlockObserver;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.EthHashBlockCreator;
import net.consensys.pantheon.ethereum.mainnet.EthHashSolution;
import net.consensys.pantheon.ethereum.mainnet.EthHashSolverInputs;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.util.Subscribers;

import java.util.Optional;

/**
 * Provides the EthHash specific aspects of the mining operation - i.e. getting the work definition,
 * reporting the hashrate of the miner and accepting work submissions.
 *
 * <p>All other aspects of mining (i.e. pre-block delays, block creation and importing to the chain)
 * are all conducted by the parent class.
 */
public class EthHashBlockMiner extends BlockMiner<Void, EthHashBlockCreator> {

  public EthHashBlockMiner(
      final EthHashBlockCreator blockCreator,
      final ProtocolSchedule<Void> protocolSchedule,
      final ProtocolContext<Void> protocolContext,
      final Subscribers<MinedBlockObserver> observers,
      final AbstractBlockScheduler scheduler,
      final BlockHeader parentHeader) {
    super(blockCreator, protocolSchedule, protocolContext, observers, scheduler, parentHeader);
  }

  public Optional<EthHashSolverInputs> getWorkDefinition() {
    return blockCreator.getWorkDefinition();
  }

  public Optional<Long> getHashesPerSecond() {
    return blockCreator.getHashesPerSecond();
  }

  public boolean submitWork(final EthHashSolution solution) {
    return blockCreator.submitWork(solution);
  }
}
