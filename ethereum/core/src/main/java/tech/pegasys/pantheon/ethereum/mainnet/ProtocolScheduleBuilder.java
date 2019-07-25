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
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionValidator;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProtocolScheduleBuilder<C> {

  private static final Logger LOG = LogManager.getLogger();
  private final GenesisConfigOptions config;
  private final Function<ProtocolSpecBuilder<Void>, ProtocolSpecBuilder<C>> protocolSpecAdapter;
  private final Optional<BigInteger> defaultChainId;
  private final PrivacyParameters privacyParameters;
  private final boolean isRevertReasonEnabled;

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final BigInteger defaultChainId,
      final Function<ProtocolSpecBuilder<Void>, ProtocolSpecBuilder<C>> protocolSpecAdapter,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled) {
    this(
        config,
        Optional.of(defaultChainId),
        protocolSpecAdapter,
        privacyParameters,
        isRevertReasonEnabled);
  }

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final Function<ProtocolSpecBuilder<Void>, ProtocolSpecBuilder<C>> protocolSpecAdapter,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled) {
    this(config, Optional.empty(), protocolSpecAdapter, privacyParameters, isRevertReasonEnabled);
  }

  private ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final Optional<BigInteger> defaultChainId,
      final Function<ProtocolSpecBuilder<Void>, ProtocolSpecBuilder<C>> protocolSpecAdapter,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled) {
    this.config = config;
    this.defaultChainId = defaultChainId;
    this.protocolSpecAdapter = protocolSpecAdapter;
    this.privacyParameters = privacyParameters;
    this.isRevertReasonEnabled = isRevertReasonEnabled;
  }

  public ProtocolSchedule<C> createProtocolSchedule() {
    final Optional<BigInteger> chainId =
        config.getChainId().map(Optional::of).orElse(defaultChainId);
    final MutableProtocolSchedule<C> protocolSchedule = new MutableProtocolSchedule<>(chainId);

    validateForkOrdering();

    addProtocolSpec(
        protocolSchedule,
        OptionalLong.of(0),
        MainnetProtocolSpecs.frontierDefinition(
            config.getContractSizeLimit(), config.getEvmStackSize()));
    addProtocolSpec(
        protocolSchedule,
        config.getHomesteadBlockNumber(),
        MainnetProtocolSpecs.homesteadDefinition(
            config.getContractSizeLimit(), config.getEvmStackSize()));

    config
        .getDaoForkBlock()
        .ifPresent(
            daoBlockNumber -> {
              final ProtocolSpec<C> originalProtocolSpec =
                  protocolSchedule.getByBlockNumber(daoBlockNumber);
              addProtocolSpec(
                  protocolSchedule,
                  OptionalLong.of(daoBlockNumber),
                  MainnetProtocolSpecs.daoRecoveryInitDefinition(
                      config.getContractSizeLimit(), config.getEvmStackSize()));
              addProtocolSpec(
                  protocolSchedule,
                  OptionalLong.of(daoBlockNumber + 1),
                  MainnetProtocolSpecs.daoRecoveryTransitionDefinition(
                      config.getContractSizeLimit(), config.getEvmStackSize()));

              // Return to the previous protocol spec after the dao fork has completed.
              protocolSchedule.putMilestone(daoBlockNumber + 10, originalProtocolSpec);
            });

    addProtocolSpec(
        protocolSchedule,
        config.getTangerineWhistleBlockNumber(),
        MainnetProtocolSpecs.tangerineWhistleDefinition(
            config.getContractSizeLimit(), config.getEvmStackSize()));
    addProtocolSpec(
        protocolSchedule,
        config.getSpuriousDragonBlockNumber(),
        MainnetProtocolSpecs.spuriousDragonDefinition(
            chainId, config.getContractSizeLimit(), config.getEvmStackSize()));
    addProtocolSpec(
        protocolSchedule,
        config.getByzantiumBlockNumber(),
        MainnetProtocolSpecs.byzantiumDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled));
    addProtocolSpec(
        protocolSchedule,
        config.getConstantinopleBlockNumber(),
        MainnetProtocolSpecs.constantinopleDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled));
    addProtocolSpec(
        protocolSchedule,
        config.getConstantinopleFixBlockNumber(),
        MainnetProtocolSpecs.constantinopleFixDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled));
    addProtocolSpec(
        protocolSchedule,
        config.getIstanbulBlockNumber(),
        MainnetProtocolSpecs.istanbulDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled));

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
                    .privateTransactionValidatorBuilder(
                        () -> new PrivateTransactionValidator(protocolSchedule.getChainId()))
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
    lastForkBlock = validateForkOrder("Istanbul", config.getIstanbulBlockNumber(), lastForkBlock);
    assert (lastForkBlock >= 0);
  }
}
