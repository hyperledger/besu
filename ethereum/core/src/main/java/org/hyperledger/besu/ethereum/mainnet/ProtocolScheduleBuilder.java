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
import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionValidator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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
  private final BadBlockManager badBlockManager;
  private final boolean isParallelTxProcessingEnabled;
  private final MetricsSystem metricsSystem;
  private final MiningConfiguration miningConfiguration;

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final Optional<BigInteger> defaultChainId,
      final ProtocolSpecAdapters protocolSpecAdapters,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    this.config = config;
    this.protocolSpecAdapters = protocolSpecAdapters;
    this.privacyParameters = privacyParameters;
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    this.evmConfiguration = evmConfiguration;
    this.defaultChainId = defaultChainId;
    this.badBlockManager = badBlockManager;
    this.isParallelTxProcessingEnabled = isParallelTxProcessingEnabled;
    this.metricsSystem = metricsSystem;
    this.miningConfiguration = miningConfiguration;
  }

  public ProtocolSchedule createProtocolSchedule() {
    final Optional<BigInteger> chainId = config.getChainId().or(() -> defaultChainId);
    DefaultProtocolSchedule protocolSchedule = new DefaultProtocolSchedule(chainId);
    initSchedule(protocolSchedule, chainId);
    return protocolSchedule;
  }

  public void initSchedule(
      final ProtocolSchedule protocolSchedule, final Optional<BigInteger> chainId) {

    final MainnetProtocolSpecFactory specFactory =
        new MainnetProtocolSpecFactory(
            chainId,
            isRevertReasonEnabled,
            config.getEcip1017EraRounds(),
            evmConfiguration.overrides(
                config.getContractSizeLimit(), OptionalInt.empty(), config.getEvmStackSize()),
            miningConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem);

    validateForkOrdering();

    final List<BuilderMapEntry> mileStones = createMilestones(specFactory);
    final Map<HardforkId, Long> completeMileStoneList = buildFullMilestoneMap(mileStones);
    protocolSchedule.setMilestones(completeMileStoneList);

    final NavigableMap<Long, BuilderMapEntry> builders = buildFlattenedMilestoneMap(mileStones);

    // At this stage, all milestones are flagged with the correct modifier, but ProtocolSpecs must
    // be
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
                        parent.hardforkId,
                        parent.milestoneType,
                        modifierBlock,
                        parent.builder(),
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
                    e.blockIdentifier(),
                    e.builder(),
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
                      previousSpecBuilder.builder(),
                      previousSpecBuilder.modifier());
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
                      previousSpecBuilder.builder(),
                      previousSpecBuilder.modifier());
              addProtocolSpec(
                  protocolSchedule,
                  BuilderMapEntry.MilestoneType.BLOCK_NUMBER,
                  classicBlockNumber,
                  ClassicProtocolSpecs.classicRecoveryInitDefinition(
                      evmConfiguration, isParallelTxProcessingEnabled, metricsSystem),
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
    lastForkBlock = validateForkOrder("CancunEOF", config.getCancunEOFTime(), lastForkBlock);
    lastForkBlock = validateForkOrder("Prague", config.getPragueTime(), lastForkBlock);
    lastForkBlock = validateForkOrder("Osaka", config.getOsakaTime(), lastForkBlock);
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

  private NavigableMap<Long, BuilderMapEntry> buildFlattenedMilestoneMap(
      final List<BuilderMapEntry> mileStones) {
    return mileStones.stream()
        .collect(
            Collectors.toMap(
                BuilderMapEntry::blockIdentifier,
                b -> b,
                (existing, replacement) -> replacement,
                TreeMap::new));
  }

  private Map<HardforkId, Long> buildFullMilestoneMap(final List<BuilderMapEntry> mileStones) {
    return mileStones.stream()
        .collect(
            Collectors.toMap(
                b -> b.hardforkId,
                BuilderMapEntry::blockIdentifier,
                (existing, replacement) -> existing));
  }

  private List<BuilderMapEntry> createMilestones(final MainnetProtocolSpecFactory specFactory) {
    List<Optional<BuilderMapEntry>> retval = new ArrayList<>();

    retval.add(
        blockNumberMilestone(
            HardforkId.MainnetHardforkId.FRONTIER,
            OptionalLong.of(0),
            specFactory.frontierDefinition()));
    if (config.getHomesteadBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.HOMESTEAD,
              config.getHomesteadBlockNumber(),
              specFactory.homesteadDefinition()));
    }
    if (config.getTangerineWhistleBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.TANGERINE_WHISTLE,
              config.getTangerineWhistleBlockNumber(),
              specFactory.tangerineWhistleDefinition()));
    }
    if (config.getSpuriousDragonBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.SPURIOUS_DRAGON,
              config.getSpuriousDragonBlockNumber(),
              specFactory.spuriousDragonDefinition()));
    }
    if (config.getByzantiumBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.BYZANTIUM,
              config.getByzantiumBlockNumber(),
              specFactory.byzantiumDefinition()));
    }
    if (config.getConstantinopleBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.CONSTANTINOPLE,
              config.getConstantinopleBlockNumber(),
              specFactory.constantinopleDefinition()));
    }
    if (config.getPetersburgBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.PETERSBURG,
              config.getPetersburgBlockNumber(),
              specFactory.petersburgDefinition()));
    }
    if (config.getIstanbulBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.ISTANBUL,
              config.getIstanbulBlockNumber(),
              specFactory.istanbulDefinition()));
    }
    if (config.getMuirGlacierBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.MUIR_GLACIER,
              config.getMuirGlacierBlockNumber(),
              specFactory.muirGlacierDefinition()));
    }
    if (config.getBerlinBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.BERLIN,
              config.getBerlinBlockNumber(),
              specFactory.berlinDefinition()));
    }
    if (config.getLondonBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.LONDON,
              config.getLondonBlockNumber(),
              specFactory.londonDefinition(config)));
    }
    if (config.getArrowGlacierBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.ARROW_GLACIER,
              config.getArrowGlacierBlockNumber(),
              specFactory.arrowGlacierDefinition(config)));
    }
    if (config.getGrayGlacierBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.GRAY_GLACIER,
              config.getGrayGlacierBlockNumber(),
              specFactory.grayGlacierDefinition(config)));
    }
    if (config.getMergeNetSplitBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.MainnetHardforkId.PARIS,
              config.getMergeNetSplitBlockNumber(),
              specFactory.parisDefinition(config)));
    }
    // Timestamp Forks
    if (config.getShanghaiTime().isPresent()) {
      retval.add(
          timestampMilestone(
              HardforkId.MainnetHardforkId.SHANGHAI,
              config.getShanghaiTime(),
              specFactory.shanghaiDefinition(config)));
    }
    if (config.getCancunTime().isPresent()) {
      retval.add(
          timestampMilestone(
              HardforkId.MainnetHardforkId.CANCUN,
              config.getCancunTime(),
              specFactory.cancunDefinition(config)));
    }
    if (config.getCancunEOFTime().isPresent()) {
      retval.add(
          timestampMilestone(
              HardforkId.MainnetHardforkId.CANCUN_EOF,
              config.getCancunEOFTime(),
              specFactory.cancunEOFDefinition(config)));
    }
    if (config.getPragueTime().isPresent()) {
      retval.add(
          timestampMilestone(
              HardforkId.MainnetHardforkId.PRAGUE,
              config.getPragueTime(),
              specFactory.pragueDefinition(config)));
    }
    if (config.getOsakaTime().isPresent()) {
      retval.add(
          timestampMilestone(
              MainnetHardforkId.OSAKA, config.getOsakaTime(), specFactory.osakaDefinition(config)));
    }
    if (config.getFutureEipsTime().isPresent()) {
      retval.add(
          timestampMilestone(
              HardforkId.MainnetHardforkId.FUTURE_EIPS,
              config.getFutureEipsTime(),
              specFactory.futureEipsDefinition(config)));
    }
    if (config.getExperimentalEipsTime().isPresent()) {
      retval.add(
          timestampMilestone(
              HardforkId.MainnetHardforkId.EXPERIMENTAL_EIPS,
              config.getExperimentalEipsTime(),
              specFactory.experimentalEipsDefinition(config)));
    }
    // Classic Milestones
    if (config.getEcip1015BlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.ClassicHardforkId.CLASSIC_TANGERINE_WHISTLE,
              config.getEcip1015BlockNumber(),
              specFactory.tangerineWhistleDefinition()));
    }
    if (config.getDieHardBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.ClassicHardforkId.DIE_HARD,
              config.getDieHardBlockNumber(),
              specFactory.dieHardDefinition()));
    }
    if (config.getGothamBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.ClassicHardforkId.GOTHAM,
              config.getGothamBlockNumber(),
              specFactory.gothamDefinition()));
    }
    if (config.getDefuseDifficultyBombBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.ClassicHardforkId.DEFUSE_DIFFICULTY_BOMB,
              config.getDefuseDifficultyBombBlockNumber(),
              specFactory.defuseDifficultyBombDefinition()));
    }
    if (config.getAtlantisBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.ClassicHardforkId.ATLANTIS,
              config.getAtlantisBlockNumber(),
              specFactory.atlantisDefinition()));
    }
    if (config.getAghartaBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.ClassicHardforkId.AGHARTA,
              config.getAghartaBlockNumber(),
              specFactory.aghartaDefinition()));
    }
    if (config.getPhoenixBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.ClassicHardforkId.PHOENIX,
              config.getPhoenixBlockNumber(),
              specFactory.phoenixDefinition()));
    }
    if (config.getThanosBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.ClassicHardforkId.THANOS,
              config.getThanosBlockNumber(),
              specFactory.thanosDefinition()));
    }
    if (config.getMagnetoBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.ClassicHardforkId.MAGNETO,
              config.getMagnetoBlockNumber(),
              specFactory.magnetoDefinition()));
    }
    if (config.getMystiqueBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.ClassicHardforkId.MYSTIQUE,
              config.getMystiqueBlockNumber(),
              specFactory.mystiqueDefinition()));
    }
    if (config.getSpiralBlockNumber().isPresent()) {
      retval.add(
          blockNumberMilestone(
              HardforkId.ClassicHardforkId.SPIRAL,
              config.getSpiralBlockNumber(),
              specFactory.spiralDefinition()));
    }

    return retval.stream().flatMap(Optional::stream).toList();
  }

  private Optional<BuilderMapEntry> timestampMilestone(
      final HardforkId hardforkId,
      final OptionalLong blockIdentifier,
      final ProtocolSpecBuilder builder) {
    return createMilestone(
        hardforkId, blockIdentifier, builder, BuilderMapEntry.MilestoneType.TIMESTAMP);
  }

  private Optional<BuilderMapEntry> blockNumberMilestone(
      final HardforkId hardforkId,
      final OptionalLong blockIdentifier,
      final ProtocolSpecBuilder builder) {
    return createMilestone(
        hardforkId, blockIdentifier, builder, BuilderMapEntry.MilestoneType.BLOCK_NUMBER);
  }

  private Optional<BuilderMapEntry> createMilestone(
      final HardforkId hardforkId,
      final OptionalLong blockIdentifier,
      final ProtocolSpecBuilder builder,
      final BuilderMapEntry.MilestoneType milestoneType) {
    if (blockIdentifier.isEmpty()) {
      return Optional.empty();
    }
    final long blockVal = blockIdentifier.getAsLong();
    return Optional.of(
        new BuilderMapEntry(
            hardforkId,
            milestoneType,
            blockVal,
            builder,
            protocolSpecAdapters.getModifierForBlock(blockVal)));
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
      case BLOCK_NUMBER ->
          protocolSchedule.putBlockNumberMilestone(
              blockNumberOrTimestamp, getProtocolSpec(protocolSchedule, definition, modifier));
      case TIMESTAMP ->
          protocolSchedule.putTimestampMilestone(
              blockNumberOrTimestamp, getProtocolSpec(protocolSchedule, definition, modifier));
      default ->
          throw new IllegalStateException(
              "Unexpected milestoneType: "
                  + milestoneType
                  + " for milestone: "
                  + blockNumberOrTimestamp);
    }
  }

  private record BuilderMapEntry(
      HardforkId hardforkId,
      ProtocolScheduleBuilder.BuilderMapEntry.MilestoneType milestoneType,
      long blockIdentifier,
      ProtocolSpecBuilder builder,
      Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier) {

    private enum MilestoneType {
      BLOCK_NUMBER,
      TIMESTAMP
    }
  }

  public Optional<BigInteger> getDefaultChainId() {
    return defaultChainId;
  }
}
