/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;

import java.util.OptionalLong;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProtocolScheduleBuilder<C> {
  private static final Logger LOG = LogManager.getLogger();
  private final GenesisConfigOptions config;
  private final Function<ProtocolSpecBuilder<Void>, ProtocolSpecBuilder<C>> protocolSpecAdapter;
  private final int defaultChainId;
  private final PrivacyParameters privacyParameters;

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final int defaultChainId,
      final Function<ProtocolSpecBuilder<Void>, ProtocolSpecBuilder<C>> protocolSpecAdapter,
      final PrivacyParameters privacyParameters) {
    this.config = config;
    this.protocolSpecAdapter = protocolSpecAdapter;
    this.defaultChainId = defaultChainId;
    this.privacyParameters = privacyParameters;
  }

  public ProtocolSchedule<C> createProtocolSchedule() {
    final int chainId = config.getChainId().orElse(defaultChainId);
    final MutableProtocolSchedule<C> protocolSchedule = new MutableProtocolSchedule<>(chainId);

    validateForkOrdering();

    addProtocolSpec(
        protocolSchedule, OptionalLong.of(0), MainnetProtocolSpecs.frontierDefinition());
    addProtocolSpec(
        protocolSchedule,
        config.getHomesteadBlockNumber(),
        MainnetProtocolSpecs.homesteadDefinition());

    config
        .getDaoForkBlock()
        .ifPresent(
            daoBlockNumber -> {
              if (daoBlockNumber > 0) {
                final ProtocolSpec<C> originalProtocolSpec =
                    protocolSchedule.getByBlockNumber(daoBlockNumber);
                addProtocolSpec(
                    protocolSchedule,
                    OptionalLong.of(daoBlockNumber),
                    MainnetProtocolSpecs.daoRecoveryInitDefinition());
                addProtocolSpec(
                    protocolSchedule,
                    OptionalLong.of(daoBlockNumber + 1),
                    MainnetProtocolSpecs.daoRecoveryTransitionDefinition());

                // Return to the previous protocol spec after the dao fork has completed.
                protocolSchedule.putMilestone(daoBlockNumber + 10, originalProtocolSpec);
              }
            });

    addProtocolSpec(
        protocolSchedule,
        config.getTangerineWhistleBlockNumber(),
        MainnetProtocolSpecs.tangerineWhistleDefinition());
    addProtocolSpec(
        protocolSchedule,
        config.getSpuriousDragonBlockNumber(),
        MainnetProtocolSpecs.spuriousDragonDefinition(chainId));
    addProtocolSpec(
        protocolSchedule,
        config.getByzantiumBlockNumber(),
        MainnetProtocolSpecs.byzantiumDefinition(chainId));
    addProtocolSpec(
        protocolSchedule,
        config.getConstantinopleBlockNumber(),
        MainnetProtocolSpecs.constantinopleDefinition(chainId));
    addProtocolSpec(
        protocolSchedule,
        config.getConstantinopleFixBlockNumber(),
        MainnetProtocolSpecs.constantinopleFixDefinition(chainId));

    LOG.info("Protocol schedule created with milestones: {}", protocolSchedule.listMilestones());
    return protocolSchedule;
  }

  private void addProtocolSpec(
      final MutableProtocolSchedule<C> protocolSchedule,
      final OptionalLong blockNumber,
      final ProtocolSpecBuilder<Void> definition) {
    blockNumber.ifPresent(
        number ->
            protocolSchedule.putMilestone(
                number,
                protocolSpecAdapter
                    .apply(definition)
                    .privacyParameters(privacyParameters)
                    .build(protocolSchedule)));
  }

  private long validateForkOrder(
      final String forkName, final OptionalLong thisForkBlock, final long lastForkBlock) {
    final long referenceForkBlock = thisForkBlock.orElse(lastForkBlock);
    if (lastForkBlock > referenceForkBlock) {
      throw new RuntimeException(
          String.format(
              "Genesis Config Error: '%s' is scheduled for block %d but it must be on or after block %d.",
              forkName, thisForkBlock.getAsLong(), lastForkBlock));
    }
    return referenceForkBlock;
  }

  private void validateForkOrdering() {
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
        validateForkOrder(
            "ConstantinopleFix", config.getConstantinopleFixBlockNumber(), lastForkBlock);
    assert (lastForkBlock >= 0);
  }
}
