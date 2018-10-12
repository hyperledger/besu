package tech.pegasys.pantheon.consensus.clique;

import static tech.pegasys.pantheon.consensus.clique.BlockHeaderValidationRulesetFactory.cliqueBlockHeaderValidator;

import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockBodyValidator;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockImporter;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSpecs;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpecBuilder;

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
