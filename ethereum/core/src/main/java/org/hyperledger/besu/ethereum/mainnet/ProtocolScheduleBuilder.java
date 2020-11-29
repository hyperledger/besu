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
import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionValidator;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProtocolScheduleBuilder {

  private static final Logger LOG = LogManager.getLogger();
  private final GenesisConfigOptions config;
  private final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> protocolSpecAdapter;
  private final Optional<BigInteger> defaultChainId;
  private final PrivacyParameters privacyParameters;
  private final boolean isRevertReasonEnabled;
  private final BadBlockManager badBlockManager = new BadBlockManager();
  private final boolean quorumCompatibilityMode;

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final BigInteger defaultChainId,
      final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> protocolSpecAdapter,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode) {
    this(
        config,
        Optional.of(defaultChainId),
        protocolSpecAdapter,
        privacyParameters,
        isRevertReasonEnabled,
        quorumCompatibilityMode);
  }

  public ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> protocolSpecAdapter,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode) {
    this(
        config,
        Optional.empty(),
        protocolSpecAdapter,
        privacyParameters,
        isRevertReasonEnabled,
        quorumCompatibilityMode);
  }

  private ProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final Optional<BigInteger> defaultChainId,
      final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> protocolSpecAdapter,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode) {
    this.config = config;
    this.defaultChainId = defaultChainId;
    this.protocolSpecAdapter = protocolSpecAdapter;
    this.privacyParameters = privacyParameters;
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    this.quorumCompatibilityMode = quorumCompatibilityMode;
  }

  public ProtocolSchedule createProtocolSchedule() {
    final Optional<BigInteger> chainId =
        config.getChainId().map(Optional::of).orElse(defaultChainId);
    final MutableProtocolSchedule protocolSchedule = new MutableProtocolSchedule(chainId);

    validateForkOrdering();

    addProtocolSpec(
        protocolSchedule,
        OptionalLong.of(0),
        MainnetProtocolSpecs.frontierDefinition(
            config.getContractSizeLimit(), config.getEvmStackSize(), quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getHomesteadBlockNumber(),
        MainnetProtocolSpecs.homesteadDefinition(
            config.getContractSizeLimit(), config.getEvmStackSize(), quorumCompatibilityMode));

    config
        .getDaoForkBlock()
        .ifPresent(
            daoBlockNumber -> {
              final ProtocolSpec originalProtocolSpec =
                  protocolSchedule.getByBlockNumber(daoBlockNumber);
              addProtocolSpec(
                  protocolSchedule,
                  OptionalLong.of(daoBlockNumber),
                  MainnetProtocolSpecs.daoRecoveryInitDefinition(
                      config.getContractSizeLimit(),
                      config.getEvmStackSize(),
                      quorumCompatibilityMode));
              addProtocolSpec(
                  protocolSchedule,
                  OptionalLong.of(daoBlockNumber + 1),
                  MainnetProtocolSpecs.daoRecoveryTransitionDefinition(
                      config.getContractSizeLimit(),
                      config.getEvmStackSize(),
                      quorumCompatibilityMode));

              // Return to the previous protocol spec after the dao fork has completed.
              protocolSchedule.putMilestone(daoBlockNumber + 10, originalProtocolSpec);
            });

    addProtocolSpec(
        protocolSchedule,
        config.getTangerineWhistleBlockNumber(),
        MainnetProtocolSpecs.tangerineWhistleDefinition(
            config.getContractSizeLimit(), config.getEvmStackSize(), quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getSpuriousDragonBlockNumber(),
        MainnetProtocolSpecs.spuriousDragonDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getByzantiumBlockNumber(),
        MainnetProtocolSpecs.byzantiumDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getConstantinopleBlockNumber(),
        MainnetProtocolSpecs.constantinopleDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getConstantinopleFixBlockNumber(),
        MainnetProtocolSpecs.constantinopleFixDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getIstanbulBlockNumber(),
        MainnetProtocolSpecs.istanbulDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getMuirGlacierBlockNumber(),
        MainnetProtocolSpecs.muirGlacierDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            quorumCompatibilityMode));

    if (ExperimentalEIPs.berlinEnabled) {
      addProtocolSpec(
          protocolSchedule,
          config.getBerlinBlockNumber(),
          MainnetProtocolSpecs.berlinDefinition(
              chainId,
              config.getContractSizeLimit(),
              config.getEvmStackSize(),
              isRevertReasonEnabled,
              quorumCompatibilityMode));
    }

    if (ExperimentalEIPs.eip1559Enabled) {
      final Optional<TransactionPriceCalculator> transactionPriceCalculator =
          Optional.of(TransactionPriceCalculator.eip1559());
      addProtocolSpec(
          protocolSchedule,
          config.getEIP1559BlockNumber(),
          MainnetProtocolSpecs.eip1559Definition(
              chainId,
              transactionPriceCalculator,
              config.getContractSizeLimit(),
              config.getEvmStackSize(),
              isRevertReasonEnabled,
              config,
              quorumCompatibilityMode));
    }

    // specs for classic network
    config
        .getClassicForkBlock()
        .ifPresent(
            classicBlockNumber -> {
              final ProtocolSpec originalProtocolSpce =
                  protocolSchedule.getByBlockNumber(classicBlockNumber);
              addProtocolSpec(
                  protocolSchedule,
                  OptionalLong.of(classicBlockNumber),
                  ClassicProtocolSpecs.classicRecoveryInitDefinition(
                      config.getContractSizeLimit(),
                      config.getEvmStackSize(),
                      quorumCompatibilityMode));
              protocolSchedule.putMilestone(classicBlockNumber + 1, originalProtocolSpce);
            });

    addProtocolSpec(
        protocolSchedule,
        config.getEcip1015BlockNumber(),
        ClassicProtocolSpecs.tangerineWhistleDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getDieHardBlockNumber(),
        ClassicProtocolSpecs.dieHardDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getGothamBlockNumber(),
        ClassicProtocolSpecs.gothamDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            config.getEcip1017EraRounds(),
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getDefuseDifficultyBombBlockNumber(),
        ClassicProtocolSpecs.defuseDifficultyBombDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            config.getEcip1017EraRounds(),
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getAtlantisBlockNumber(),
        ClassicProtocolSpecs.atlantisDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            config.getEcip1017EraRounds(),
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getAghartaBlockNumber(),
        ClassicProtocolSpecs.aghartaDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            config.getEcip1017EraRounds(),
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getPhoenixBlockNumber(),
        ClassicProtocolSpecs.phoenixDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            config.getEcip1017EraRounds(),
            quorumCompatibilityMode));
    addProtocolSpec(
        protocolSchedule,
        config.getThanosBlockNumber(),
        ClassicProtocolSpecs.thanosDefinition(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            config.getEcip1017EraRounds(),
            quorumCompatibilityMode));

    LOG.info("Protocol schedule created with milestones: {}", protocolSchedule.listMilestones());
    return protocolSchedule;
  }

  private void addProtocolSpec(
      final MutableProtocolSchedule protocolSchedule,
      final OptionalLong blockNumber,
      final ProtocolSpecBuilder definition) {
    blockNumber.ifPresent(
        number ->
            protocolSchedule.putMilestone(
                number,
                protocolSpecAdapter
                    .apply(definition)
                    .badBlocksManager(badBlockManager)
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
        validateForkOrder(
            "ConstantinopleFix", config.getConstantinopleFixBlockNumber(), lastForkBlock);
    lastForkBlock = validateForkOrder("Istanbul", config.getIstanbulBlockNumber(), lastForkBlock);
    lastForkBlock =
        validateForkOrder("MuirGlacier", config.getMuirGlacierBlockNumber(), lastForkBlock);
    if (ExperimentalEIPs.berlinEnabled) {
      lastForkBlock = validateForkOrder("Berlin", config.getBerlinBlockNumber(), lastForkBlock);
    }
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
    assert (lastForkBlock >= 0);
  }
}
