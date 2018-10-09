package net.consensys.pantheon.consensus.clique;

import static net.consensys.pantheon.consensus.clique.BlockHeaderValidationRulesetFactory.cliqueBlockHeaderValidator;

import net.consensys.pantheon.consensus.common.EpochManager;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockBodyValidator;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockImporter;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSpecs;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpecBuilder;

/** Factory for producing Clique protocol specs for given configurations and known fork points */
public class CliqueProtocolSpecs {

  private final long secondsBetweenBlocks;
  private final long epochLength;
  private final int chainId;
  private final Address localNodeAddress;
  private final ProtocolSchedule<CliqueContext> protocolSchedule;

  public CliqueProtocolSpecs(
      final long secondsBetweenBlocks,
      final long epochLength,
      final int chainId,
      final Address localNodeAddress,
      final ProtocolSchedule<CliqueContext> protocolSchedule) {
    this.secondsBetweenBlocks = secondsBetweenBlocks;
    this.epochLength = epochLength;
    this.chainId = chainId;
    this.localNodeAddress = localNodeAddress;
    this.protocolSchedule = protocolSchedule;
  }

  public ProtocolSpec<CliqueContext> frontier() {
    return applyCliqueSpecificModifications(MainnetProtocolSpecs.frontierDefinition());
  }

  public ProtocolSpec<CliqueContext> homestead() {
    return applyCliqueSpecificModifications(MainnetProtocolSpecs.homesteadDefinition());
  }

  public ProtocolSpec<CliqueContext> tangerineWhistle() {
    return applyCliqueSpecificModifications(MainnetProtocolSpecs.tangerineWhistleDefinition());
  }

  public ProtocolSpec<CliqueContext> spuriousDragon() {
    return applyCliqueSpecificModifications(MainnetProtocolSpecs.spuriousDragonDefinition(chainId));
  }

  public ProtocolSpec<CliqueContext> byzantium() {
    return applyCliqueSpecificModifications(MainnetProtocolSpecs.byzantiumDefinition(chainId));
  }

  private ProtocolSpec<CliqueContext> applyCliqueSpecificModifications(
      final ProtocolSpecBuilder<Void> specBuilder) {
    final EpochManager epochManager = new EpochManager(epochLength);
    return specBuilder
        .<CliqueContext>changeConsensusContextType(
            difficultyCalculator -> cliqueBlockHeaderValidator(secondsBetweenBlocks, epochManager),
            MainnetBlockBodyValidator::new,
            MainnetBlockImporter::new,
            new CliqueDifficultyCalculator(localNodeAddress))
        .blockReward(Wei.ZERO)
        .miningBeneficiaryCalculator(CliqueHelpers::getProposerOfBlock)
        .build(protocolSchedule);
  }
}
