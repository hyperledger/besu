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
import org.hyperledger.besu.ethereum.linea.LineaParameters;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

public class ProtocolScheduleBuilder extends AbstractProtocolScheduleBuilder {

  private final Optional<BigInteger> defaultChainId;
  private MutableProtocolSchedule protocolSchedule;

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final BigInteger defaultChainId,
      final ProtocolSpecAdapters protocolSpecAdapters,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    this(
        config,
        Optional.of(defaultChainId),
        protocolSpecAdapters,
        privacyParameters,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration,
        LineaParameters.DEFAULT);
  }

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final BigInteger defaultChainId,
      final ProtocolSpecAdapters protocolSpecAdapters,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration,
      final LineaParameters lineaParameters) {
    this(
        config,
        Optional.of(defaultChainId),
        protocolSpecAdapters,
        privacyParameters,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration,
        lineaParameters);
  }

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final ProtocolSpecAdapters protocolSpecAdapters,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    this(
        config,
        protocolSpecAdapters,
        privacyParameters,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration,
        LineaParameters.DEFAULT);
  }

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final ProtocolSpecAdapters protocolSpecAdapters,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration,
      final LineaParameters lineaParameters) {
    this(
        config,
        Optional.empty(),
        protocolSpecAdapters,
        privacyParameters,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration,
        lineaParameters);
  }

  private ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final Optional<BigInteger> defaultChainId,
      final ProtocolSpecAdapters protocolSpecAdapters,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration,
      final LineaParameters lineaParameters) {
    super(
        config,
        protocolSpecAdapters,
        privacyParameters,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration,
        lineaParameters);
    this.defaultChainId = defaultChainId;
  }

  public ProtocolSchedule createProtocolSchedule() {
    final Optional<BigInteger> chainId = config.getChainId().or(() -> defaultChainId);
    protocolSchedule = new MutableProtocolSchedule(chainId);
    initSchedule(protocolSchedule, chainId);
    return protocolSchedule;
  }

  @Override
  protected void validateForkOrdering() {
    if (config.getDaoForkBlock().isEmpty() && config.getLineaBlockNumber().isEmpty()) {
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
    lastForkBlock = validateForkOrder("Linea", config.getLineaBlockNumber(), lastForkBlock);

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
        create(OptionalLong.of(0), specFactory.frontierDefinition()),
        create(config.getHomesteadBlockNumber(), specFactory.homesteadDefinition()),
        create(config.getTangerineWhistleBlockNumber(), specFactory.tangerineWhistleDefinition()),
        create(config.getSpuriousDragonBlockNumber(), specFactory.spuriousDragonDefinition()),
        create(config.getByzantiumBlockNumber(), specFactory.byzantiumDefinition()),
        create(config.getConstantinopleBlockNumber(), specFactory.constantinopleDefinition()),
        create(config.getPetersburgBlockNumber(), specFactory.petersburgDefinition()),
        create(config.getIstanbulBlockNumber(), specFactory.istanbulDefinition()),
        create(config.getMuirGlacierBlockNumber(), specFactory.muirGlacierDefinition()),
        create(config.getBerlinBlockNumber(), specFactory.berlinDefinition()),
        create(config.getLondonBlockNumber(), specFactory.londonDefinition(config)),
        create(config.getArrowGlacierBlockNumber(), specFactory.arrowGlacierDefinition(config)),
        create(config.getGrayGlacierBlockNumber(), specFactory.grayGlacierDefinition(config)),
        create(config.getMergeNetSplitBlockNumber(), specFactory.parisDefinition(config)),
        create(config.getLineaBlockNumber(), specFactory.lineaDefinition(config, lineaParameters)),
        // Classic Milestones
        create(config.getEcip1015BlockNumber(), specFactory.tangerineWhistleDefinition()),
        create(config.getDieHardBlockNumber(), specFactory.dieHardDefinition()),
        create(config.getGothamBlockNumber(), specFactory.gothamDefinition()),
        create(
            config.getDefuseDifficultyBombBlockNumber(),
            specFactory.defuseDifficultyBombDefinition()),
        create(config.getAtlantisBlockNumber(), specFactory.atlantisDefinition()),
        create(config.getAghartaBlockNumber(), specFactory.aghartaDefinition()),
        create(config.getPhoenixBlockNumber(), specFactory.phoenixDefinition()),
        create(config.getThanosBlockNumber(), specFactory.thanosDefinition()),
        create(config.getMagnetoBlockNumber(), specFactory.magnetoDefinition()),
        create(config.getMystiqueBlockNumber(), specFactory.mystiqueDefinition()),
        create(config.getEcip1049BlockNumber(), specFactory.ecip1049Definition()));
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
                  daoBlockNumber,
                  specFactory.daoRecoveryInitDefinition(),
                  protocolSpecAdapters.getModifierForBlock(daoBlockNumber));
              addProtocolSpec(
                  protocolSchedule,
                  daoBlockNumber + 1L,
                  specFactory.daoRecoveryTransitionDefinition(),
                  protocolSpecAdapters.getModifierForBlock(daoBlockNumber + 1L));
              // Return to the previous protocol spec after the dao fork has completed.
              protocolSchedule.putMilestone(daoBlockNumber + 10, originalProtocolSpec);
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
                  classicBlockNumber,
                  ClassicProtocolSpecs.classicRecoveryInitDefinition(
                      config.getContractSizeLimit(),
                      config.getEvmStackSize(),
                      quorumCompatibilityMode,
                      evmConfiguration),
                  Function.identity());
              protocolSchedule.putMilestone(classicBlockNumber + 1, originalProtocolSpec);
            });
  }
}
