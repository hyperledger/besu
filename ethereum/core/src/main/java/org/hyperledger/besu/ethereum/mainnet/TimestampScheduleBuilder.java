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

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionValidator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimestampScheduleBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(TimestampScheduleBuilder.class);
    private final GenesisConfigOptions config;
    private final ProtocolSpecAdapters protocolSpecAdapters;
    private final Optional<BigInteger> defaultChainId;
    private final PrivacyParameters privacyParameters;
    private final boolean isRevertReasonEnabled;
    private final BadBlockManager badBlockManager = new BadBlockManager();
    private final boolean quorumCompatibilityMode;
    private final EvmConfiguration evmConfiguration;
    public TimestampScheduleBuilder(
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
                evmConfiguration);
    }

    public TimestampScheduleBuilder(
            final GenesisConfigOptions config,
            final ProtocolSpecAdapters protocolSpecAdapters,
            final PrivacyParameters privacyParameters,
            final boolean isRevertReasonEnabled,
            final boolean quorumCompatibilityMode,
            final EvmConfiguration evmConfiguration) {
        this(
                config,
                Optional.empty(),
                protocolSpecAdapters,
                privacyParameters,
                isRevertReasonEnabled,
                quorumCompatibilityMode,
                evmConfiguration);
    }

    private TimestampScheduleBuilder(
            final GenesisConfigOptions config,
            final Optional<BigInteger> defaultChainId,
            final ProtocolSpecAdapters protocolSpecAdapters,
            final PrivacyParameters privacyParameters,
            final boolean isRevertReasonEnabled,
            final boolean quorumCompatibilityMode,
            final EvmConfiguration evmConfiguration) {
        this.config = config;
        this.defaultChainId = defaultChainId;
        this.protocolSpecAdapters = protocolSpecAdapters;
        this.privacyParameters = privacyParameters;
        this.isRevertReasonEnabled = isRevertReasonEnabled;
        this.quorumCompatibilityMode = quorumCompatibilityMode;
        this.evmConfiguration = evmConfiguration;
    }

    private Optional<BuilderMapEntry> create(
            final OptionalLong optionalTimestamp, final ProtocolSpecBuilder builder) {
        if (optionalTimestamp.isEmpty()) {
            return Optional.empty();
        }
        final long timestamp = optionalTimestamp.getAsLong();
        return Optional.of(
                new BuilderMapEntry(timestamp, builder, protocolSpecAdapters.getModifierForBlock(timestamp)));
    }

    public TimestampSchedule createTimeStampSchedule() {
        final Optional<BigInteger> chainId = config.getChainId().or(() -> defaultChainId);
        final MainNetTimestampSchedule timestampSchedule = new MainNetTimestampSchedule(chainId);

        final MainnetProtocolSpecFactory specFactory =
                new MainnetProtocolSpecFactory(
                        chainId,
                        config.getContractSizeLimit(),
                        config.getEvmStackSize(),
                        isRevertReasonEnabled,
                        quorumCompatibilityMode,
                        config.getEcip1017EraRounds(),
                        evmConfiguration);

        validateForkOrdering();

        final TreeMap<Long, BuilderMapEntry> builders = buildMilestoneMap(specFactory);

        // At this stage, all milestones are flagged with correct modifier, but ProtocolSpecs must be
        // inserted _AT_ the modifier block entry.
        protocolSpecAdapters.stream()
                .forEach(
                        entry -> {
                            final long modifierBlock = entry.getKey();
                            final BuilderMapEntry parent = builders.floorEntry(modifierBlock).getValue();
                            builders.put(
                                    modifierBlock,
                                    new BuilderMapEntry(modifierBlock, parent.getBuilder(), entry.getValue()));
                        });

        // Create the ProtocolSchedule, such that the Dao/fork milestones can be inserted
        builders
                .values()
                .forEach(e -> addProtocolSpec(timestampSchedule, e.getTimestamp(), e.getBuilder(), e.modifier));


        LOG.info("Protocol schedule created with milestones: {}", timestampSchedule.listMilestones());
        return timestampSchedule;
    }

    private TreeMap<Long, BuilderMapEntry> buildMilestoneMap(
            final MainnetProtocolSpecFactory specFactory) {
        return Stream.of(
                        create(config.getShanghaiTimestamp(), specFactory.shanghaiDefinition(config)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(
                        Collectors.toMap(
                                BuilderMapEntry::getTimestamp,
                                b -> b,
                                (existing, replacement) -> replacement,
                                TreeMap::new));
    }

    private void addProtocolSpec(
            final MainNetTimestampSchedule protocolSchedule,
            final long timestamp,
            final ProtocolSpecBuilder definition,
            final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier) {
        definition
                .badBlocksManager(badBlockManager)
                .privacyParameters(privacyParameters)
                .privateTransactionValidatorBuilder(
                        () -> new PrivateTransactionValidator(protocolSchedule.getChainId()));

        protocolSchedule.putMilestone(timestamp, modifier.apply(definition).build(protocolSchedule));
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
        lastForkBlock = validateForkOrder("Shandong", config.getShandongBlockNumber(), lastForkBlock);
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

    private static class BuilderMapEntry {

        private final long timestamp;
        private final ProtocolSpecBuilder builder;
        private final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier;

        public BuilderMapEntry(
                final long timestamp,
                final ProtocolSpecBuilder builder,
                final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier) {
            this.timestamp = timestamp;
            this.builder = builder;
            this.modifier = modifier;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public ProtocolSpecBuilder getBuilder() {
            return builder;
        }
    }
}
