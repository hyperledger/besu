package net.consensys.pantheon.consensus.ibft;

import static net.consensys.pantheon.consensus.ibft.IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator;

import net.consensys.pantheon.consensus.common.EpochManager;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockBodyValidator;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockImporter;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSpecs;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;

import java.math.BigInteger;

/** Factory for producing Ibft protocol specs for given configurations and known fork points */
public class IbftProtocolSpecs {

  /**
   * Produce the ProtocolSpec for an IBFT chain that uses spurious dragon milestone configuration
   *
   * @param secondsBetweenBlocks the block period in seconds
   * @param epochLength the number of blocks in each epoch
   * @param chainId the id of the Chain.
   * @param protocolSchedule the {@link ProtocolSchedule} this spec will be part of
   * @return a configured ProtocolSpec for dealing with IBFT blocks
   */
  public static ProtocolSpec<IbftContext> spuriousDragon(
      final long secondsBetweenBlocks,
      final long epochLength,
      final int chainId,
      final ProtocolSchedule<IbftContext> protocolSchedule) {
    final EpochManager epochManager = new EpochManager(epochLength);
    return MainnetProtocolSpecs.spuriousDragonDefinition(chainId)
        .<IbftContext>changeConsensusContextType(
            difficultyCalculator -> ibftBlockHeaderValidator(secondsBetweenBlocks),
            MainnetBlockBodyValidator::new,
            (blockHeaderValidator, blockBodyValidator, blockProcessor) ->
                new IbftBlockImporter(
                    new MainnetBlockImporter<>(
                        blockHeaderValidator, blockBodyValidator, blockProcessor),
                    new VoteTallyUpdater(epochManager)),
            (time, parent, protocolContext) -> BigInteger.ONE)
        .blockReward(Wei.ZERO)
        .blockHashFunction(IbftBlockHashing::calculateHashOfIbftBlockOnChain)
        .name("IBFT")
        .build(protocolSchedule);
  }
}
