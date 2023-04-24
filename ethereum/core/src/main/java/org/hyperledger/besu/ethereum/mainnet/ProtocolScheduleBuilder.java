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
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec.BlockNumberProtocolSpec;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

public class ProtocolScheduleBuilder extends AbstractProtocolScheduleBuilder {

  private final Optional<BigInteger> defaultChainId;
  private UnifiedProtocolSchedule protocolSchedule;

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
    super(config, protocolSpecAdapters, privacyParameters, isRevertReasonEnabled, evmConfiguration);
    this.defaultChainId = defaultChainId;
  }

  public ProtocolSchedule createProtocolSchedule() {
    final Optional<BigInteger> chainId = config.getChainId().or(() -> defaultChainId);
    protocolSchedule = new UnifiedProtocolSchedule(chainId);
    initSchedule(protocolSchedule, chainId);
    return protocolSchedule;
  }

  @Override
  protected void validateForkOrdering() {
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
    assert (lastForkBlock >= 0);
  }

  @Override
  protected Stream<Optional<BuilderMapEntry>> createMilestones(
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
        blockNumberMilestone(config.getMystiqueBlockNumber(), specFactory.mystiqueDefinition()));
  }

  @Override
  protected void postBuildStep(
      final MainnetProtocolSpecFactory specFactory, final TreeMap<Long, BuilderMapEntry> builders) {
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
                  BlockNumberProtocolSpec::create,
                  daoBlockNumber,
                  specFactory.daoRecoveryInitDefinition(),
                  protocolSpecAdapters.getModifierForBlock(daoBlockNumber));
              addProtocolSpec(
                  protocolSchedule,
                  BlockNumberProtocolSpec::create,
                  daoBlockNumber + 1L,
                  specFactory.daoRecoveryTransitionDefinition(),
                  protocolSpecAdapters.getModifierForBlock(daoBlockNumber + 1L));
              // Return to the previous protocol spec after the dao fork has completed.
              protocolSchedule.putMilestone(
                  BlockNumberProtocolSpec::create, daoBlockNumber + 10, originalProtocolSpec);
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
                  BlockNumberProtocolSpec::create,
                  classicBlockNumber,
                  ClassicProtocolSpecs.classicRecoveryInitDefinition(
                      config.getContractSizeLimit(), config.getEvmStackSize(), evmConfiguration),
                  Function.identity());
              protocolSchedule.putMilestone(
                  BlockNumberProtocolSpec::create, classicBlockNumber + 1, originalProtocolSpec);
            });
  }
}
