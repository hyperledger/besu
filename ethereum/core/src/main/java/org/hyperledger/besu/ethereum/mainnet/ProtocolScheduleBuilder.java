/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionValidator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtocolScheduleBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(ProtocolScheduleBuilder.class);
  private final GenesisConfigOptions config;
  private final Optional<BigInteger> defaultChainId;
  private final ProtocolSpecAdapters protocolSpecAdapters;
  private final PrivacyParameters privacyParameters;
  private final boolean isRevertReasonEnabled;
  private final EvmConfiguration evmConfiguration;
  private final BadBlockManager badBlockManager = new BadBlockManager();

  private DefaultProtocolSchedule protocolSchedule;

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final BigInteger defaultChainId,
      final ProtocolSpecAdapters protocolSpecAdapters,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration) {
    this(
        config,
        Optional.of(defaultChainId),
        protocolSpecAdapters,
        privacyParameters,
        isRevertReasonEnabled,
        evmConfiguration);
  }

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final ProtocolSpecAdapters protocolSpecAdapters,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration) {
    this(
        config,
        Optional.empty(),
        protocolSpecAdapters,
        privacyParameters,
        isRevertReasonEnabled,
        evmConfiguration);
  }

  private ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final Optional<BigInteger> defaultChainId,
      final ProtocolSpecAdapters protocolSpecAdapters,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration) {
    this.config = config;
    this.protocolSpecAdapters = protocolSpecAdapters;
    this.privacyParameters = privacyParameters;
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    this.evmConfiguration = evmConfiguration;
    this.defaultChainId = defaultChainId;
  }

  public ProtocolSchedule createProtocolSchedule() {
    final Optional<BigInteger> chainId = config.getChainId().or(() -> defaultChainId);
    protocolSchedule = new DefaultProtocolSchedule(chainId);
    initSchedule(protocolSchedule, chainId);
    return protocolSchedule;
  }

  private void initSchedule(
      final ProtocolSchedule protocolSchedule, final Optional<BigInteger> chainId) {

    final MainnetProtocolSpecFactory specFactory =
        new MainnetProtocolSpecFactory(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            config.getEcip1017EraRounds(),
            evmConfiguration,
            privacyParameters.isPrivateNonceIncrementationEnabled());

    validateForkOrdering();

    final TreeMap<Long, BuilderMapEntry> builders = buildMilestoneMap(specFactory);

    // At this stage, all milestones are flagged with correct modifier, but ProtocolSpecs must be
    // inserted _AT_ the modifier block entry.
    if (!builders.isEmpty()) {
      protocolSpecAdapters.stream()
          .forEach(
              entry -> {
                final long modifierBlock = entry.getKey();
                final BuilderMapEntry parent =
                    Optional.ofNullable(builders.floorEntry(modifierBlock))
                        .orElse(builders.firstEntry())
                        .getValue();
                builders.put(
                    modifierBlock,
                    new BuilderMapEntry(
                        parent.milestoneType,
                        modifierBlock,
                        parent.getBuilder(),
                        entry.getValue()));
              });
    }

    // Create the ProtocolSchedule, such that the Dao/fork milestones can be inserted
    builders
        .values()
        .forEach(
            e ->
                addProtocolSpec(
                    protocolSchedule,
                    e.milestoneType,
                    e.getBlockIdentifier(),
                    e.getBuilder(),
                    e.modifier));

    // NOTE: It is assumed that Daofork blocks will not be used for private networks
    // as too many risks exist around inserting a protocol-spec between daoBlock and daoBlock+10.
    config
        .getDaoForkBlock()
        .ifPresent(
            daoBlockNumber -> {
              final BuilderMapEntry previousSpecBuilder =
                  builders.floorEntry(daoBlockNumber).getValue();
              final ProtocolSpec originalProtocolSpec =
                  getProtocolSpec(
                      protocolSchedule,
                      previousSpecBuilder.getBuilder(),
                      previousSpecBuilder.getModifier());
              addProtocolSpec(
                  protocolSchedule,
                  BuilderMapEntry.MilestoneType.BLOCK_NUMBER,
                  daoBlockNumber,
                  specFactory.daoRecoveryInitDefinition(),
                  protocolSpecAdapters.getModifierForBlock(daoBlockNumber));
              addProtocolSpec(
                  protocolSchedule,
                  BuilderMapEntry.MilestoneType.BLOCK_NUMBER,
                  daoBlockNumber + 1L,
                  specFactory.daoRecoveryTransitionDefinition(),
                  protocolSpecAdapters.getModifierForBlock(daoBlockNumber + 1L));
              // Return to the previous protocol spec after the dao fork has completed.
              protocolSchedule.putBlockNumberMilestone(daoBlockNumber + 10, originalProtocolSpec);
            });

    // specs for classic network
    config
        .getClassicForkBlock()
        .ifPresent(
            classicBlockNumber -> {
              final BuilderMapEntry previousSpecBuilder =
                  builders.floorEntry(classicBlockNumber).getValue();
              final ProtocolSpec originalProtocolSpec =
                  getProtocolSpec(
                      protocolSchedule,
                      previousSpecBuilder.getBuilder(),
                      previousSpecBuilder.getModifier());
              addProtocolSpec(
                  protocolSchedule,
                  BuilderMapEntry.MilestoneType.BLOCK_NUMBER,
                  classicBlockNumber,
                  ClassicProtocolSpecs.classicRecoveryInitDefinition(
                      config.getContractSizeLimit(), config.getEvmStackSize(), evmConfiguration),
                  Function.identity());
              protocolSchedule.putBlockNumberMilestone(
                  classicBlockNumber + 1, originalProtocolSpec);
            });

    LOG.info("Protocol schedule created with milestones: {}", protocolSchedule.listMilestones());
  }

  private void validateForkOrdering() {
    if (config.getDaoForkBlock().isEmpty()) {
      validateClassicForkOrdering();
    } else {
      validateEthereumForkOrdering();
    }
  }

  private void validateEthereumForkOrdering() {
    long lastForkBlock = 0;
    lastForkBlock = validateForkOrder("Homestead", config.getHomesteadBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("DaoFork", config.getDaoForkBlock(), lastForkBlock);
    lastForkBlock =
        validateForkOrder(
            "TangerineWhistle", config.getTangerineWhistleBlockNumber(), lastForkBlock);
    lastForkBlock =
        validateForkOrder("SpuriousDragon", config.getSpuriousDragonBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Byzantium", config.getByzantiumBlockNumber(), lastForkBlock);
    lastForkBlock =
        validateForkOrder("Constantinople", config.getConstantinopleBlockNumber(), lastForkBlock);
    lastForkBlock =
        validateForkOrder("Petersburg", config.getPetersburgBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Istanbul", config.getIstanbulBlockNumber(), lastForkBlock);
    lastForkBlock =
        validateForkOrder("MuirGlacier", config.getMuirGlacierBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Berlin", config.getBerlinBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("London", config.getLondonBlockNumber(), lastForkBlock);
    lastForkBlock =
        validateForkOrder("ArrowGlacier", config.getArrowGlacierBlockNumber(), lastForkBlock);
    lastForkBlock =
        validateForkOrder("GrayGlacier", config.getGrayGlacierBlockNumber(), lastForkBlock);
    // Begin timestamp forks
    lastForkBlock = validateForkOrder("Shanghai", config.getShanghaiTime(), lastForkBlock);
    lastForkBlock = validateForkOrder("Cancun", config.getCancunTime(), lastForkBlock);
    lastForkBlock = validateForkOrder("Prague", config.getPragueTime(), lastForkBlock);
    lastForkBlock = validateForkOrder("FutureEips", config.getFutureEipsTime(), lastForkBlock);
    lastForkBlock =
        validateForkOrder("ExperimentalEips", config.getExperimentalEipsTime(), lastForkBlock);
    assert (lastForkBlock >= 0);
  }

  private void validateClassicForkOrdering() {
    long lastForkBlock = 0;
    lastForkBlock = validateForkOrder("Homestead", config.getHomesteadBlockNumber(), lastForkBlock);
    lastForkBlock =
        validateForkOrder(
            "ClassicTangerineWhistle", config.getEcip1015BlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("DieHard", config.getDieHardBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Gotham", config.getGothamBlockNumber(), lastForkBlock);
    lastForkBlock =
        validateForkOrder(
            "DefuseDifficultyBomb", config.getDefuseDifficultyBombBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Atlantis", config.getAtlantisBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Agharta", config.getAghartaBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Phoenix", config.getPhoenixBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Thanos", config.getThanosBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Magneto", config.getMagnetoBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Mystique", config.getMystiqueBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Spiral", config.getSpiralBlockNumber(), lastForkBlock);
    assert (lastForkBlock >= 0);
  }

  private long validateForkOrder(
      final String forkName, final OptionalLong thisForkBlock, final long lastForkBlock) {
    final long referenceForkBlock = thisForkBlock.orElse(lastForkBlock);
    if (lastForkBlock > referenceForkBlock) {
      throw new RuntimeException(
          String.format(
              "Genesis Config Error: '%s' is scheduled for milestone %d but it must be on or after milestone %d.",
              forkName, thisForkBlock.getAsLong(), lastForkBlock));
    }
    return referenceForkBlock;
  }

  private TreeMap<Long, BuilderMapEntry> buildMilestoneMap(
      final MainnetProtocolSpecFactory specFactory) {
    return createMilestones(specFactory)
        .flatMap(Optional::stream)
        .collect(
            Collectors.toMap(
                BuilderMapEntry::getBlockIdentifier,
                b -> b,
                (existing, replacement) -> replacement,
                TreeMap::new));
  }

  private Stream<Optional<BuilderMapEntry>> createMilestones(
      final MainnetProtocolSpecFactory specFactory) {
    return Stream.of(
        blockNumberMilestone(OptionalLong.of(0), specFactory.frontierDefinition()),
        blockNumberMilestone(config.getHomesteadBlockNumber(), specFactory.homesteadDefinition()),
        blockNumberMilestone(
            config.getTangerineWhistleBlockNumber(), specFactory.tangerineWhistleDefinition()),
        blockNumberMilestone(
            config.getSpuriousDragonBlockNumber(), specFactory.spuriousDragonDefinition()),
        blockNumberMilestone(config.getByzantiumBlockNumber(), specFactory.byzantiumDefinition()),
        blockNumberMilestone(
            config.getConstantinopleBlockNumber(), specFactory.constantinopleDefinition()),
        blockNumberMilestone(config.getPetersburgBlockNumber(), specFactory.petersburgDefinition()),
        blockNumberMilestone(config.getIstanbulBlockNumber(), specFactory.istanbulDefinition()),
        blockNumberMilestone(
            config.getMuirGlacierBlockNumber(), specFactory.muirGlacierDefinition()),
        blockNumberMilestone(config.getBerlinBlockNumber(), specFactory.berlinDefinition()),
        blockNumberMilestone(config.getLondonBlockNumber(), specFactory.londonDefinition(config)),
        blockNumberMilestone(
            config.getArrowGlacierBlockNumber(), specFactory.arrowGlacierDefinition(config)),
        blockNumberMilestone(
            config.getGrayGlacierBlockNumber(), specFactory.grayGlacierDefinition(config)),
        blockNumberMilestone(
            config.getMergeNetSplitBlockNumber(), specFactory.parisDefinition(config)),
        // Timestamp Forks
        timestampMilestone(config.getShanghaiTime(), specFactory.shanghaiDefinition(config)),
        timestampMilestone(config.getCancunTime(), specFactory.cancunDefinition(config)),
        timestampMilestone(config.getPragueTime(), specFactory.pragueDefinition(config)),
        timestampMilestone(config.getFutureEipsTime(), specFactory.futureEipsDefinition(config)),
        timestampMilestone(
            config.getExperimentalEipsTime(), specFactory.experimentalEipsDefinition(config)),

        // Classic Milestones
        blockNumberMilestone(
            config.getEcip1015BlockNumber(), specFactory.tangerineWhistleDefinition()),
        blockNumberMilestone(config.getDieHardBlockNumber(), specFactory.dieHardDefinition()),
        blockNumberMilestone(config.getGothamBlockNumber(), specFactory.gothamDefinition()),
        blockNumberMilestone(
            config.getDefuseDifficultyBombBlockNumber(),
            specFactory.defuseDifficultyBombDefinition()),
        blockNumberMilestone(config.getAtlantisBlockNumber(), specFactory.atlantisDefinition()),
        blockNumberMilestone(config.getAghartaBlockNumber(), specFactory.aghartaDefinition()),
        blockNumberMilestone(config.getPhoenixBlockNumber(), specFactory.phoenixDefinition()),
        blockNumberMilestone(config.getThanosBlockNumber(), specFactory.thanosDefinition()),
        blockNumberMilestone(config.getMagnetoBlockNumber(), specFactory.magnetoDefinition()),
        blockNumberMilestone(config.getMystiqueBlockNumber(), specFactory.mystiqueDefinition()),
        blockNumberMilestone(config.getSpiralBlockNumber(), specFactory.spiralDefinition()));
  }

  private Optional<BuilderMapEntry> timestampMilestone(
      final OptionalLong blockIdentifier, final ProtocolSpecBuilder builder) {
    return createMilestone(blockIdentifier, builder, BuilderMapEntry.MilestoneType.TIMESTAMP);
  }

  private Optional<BuilderMapEntry> blockNumberMilestone(
      final OptionalLong blockIdentifier, final ProtocolSpecBuilder builder) {
    return createMilestone(blockIdentifier, builder, BuilderMapEntry.MilestoneType.BLOCK_NUMBER);
  }

  private Optional<BuilderMapEntry> createMilestone(
      final OptionalLong blockIdentifier,
      final ProtocolSpecBuilder builder,
      final BuilderMapEntry.MilestoneType milestoneType) {
    if (blockIdentifier.isEmpty()) {
      return Optional.empty();
    }
    final long blockVal = blockIdentifier.getAsLong();
    return Optional.of(
        new BuilderMapEntry(
            milestoneType, blockVal, builder, protocolSpecAdapters.getModifierForBlock(blockVal)));
  }

  private ProtocolSpec getProtocolSpec(
      final ProtocolSchedule protocolSchedule,
      final ProtocolSpecBuilder definition,
      final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier) {
    definition
        .badBlocksManager(badBlockManager)
        .privacyParameters(privacyParameters)
        .privateTransactionValidatorBuilder(
            () -> new PrivateTransactionValidator(protocolSchedule.getChainId()));

    return modifier.apply(definition).build(protocolSchedule);
  }

  private void addProtocolSpec(
      final ProtocolSchedule protocolSchedule,
      final BuilderMapEntry.MilestoneType milestoneType,
      final long blockNumberOrTimestamp,
      final ProtocolSpecBuilder definition,
      final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier) {

    switch (milestoneType) {
      case BLOCK_NUMBER -> protocolSchedule.putBlockNumberMilestone(
          blockNumberOrTimestamp, getProtocolSpec(protocolSchedule, definition, modifier));
      case TIMESTAMP -> protocolSchedule.putTimestampMilestone(
          blockNumberOrTimestamp, getProtocolSpec(protocolSchedule, definition, modifier));
      default -> throw new IllegalStateException(
          "Unexpected milestoneType: "
              + milestoneType
              + " for milestone: "
              + blockNumberOrTimestamp);
    }
  }

  private static class BuilderMapEntry {
    private final MilestoneType milestoneType;
    private final long blockIdentifier;
    private final ProtocolSpecBuilder builder;
    private final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier;

    public BuilderMapEntry(
        final MilestoneType milestoneType,
        final long blockIdentifier,
        final ProtocolSpecBuilder builder,
        final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier) {
      this.milestoneType = milestoneType;
      this.blockIdentifier = blockIdentifier;
      this.builder = builder;
      this.modifier = modifier;
    }

    public long getBlockIdentifier() {
      return blockIdentifier;
    }

    public ProtocolSpecBuilder getBuilder() {
      return builder;
    }

    public Function<ProtocolSpecBuilder, ProtocolSpecBuilder> getModifier() {
      return modifier;
    }

    private enum MilestoneType {
      BLOCK_NUMBER,
      TIMESTAMP
    }
  }
}
