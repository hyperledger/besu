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
    this.config = config;
    this.defaultChainId = Optional.of(defaultChainId);
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
        new BuilderMapEntry(
            timestamp, builder, protocolSpecAdapters.getModifierForBlock(timestamp)));
  }

  public TimestampSchedule createTimestampSchedule() {
    final Optional<BigInteger> chainId = config.getChainId().or(() -> defaultChainId);
    final DefaultTimestampSchedule timestampSchedule = new DefaultTimestampSchedule(chainId);

    final MainnetProtocolSpecFactory specFactory =
        new MainnetProtocolSpecFactory(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            quorumCompatibilityMode,
            config.getEcip1017EraRounds(),
            evmConfiguration);

    validatorEthereumForkOrdering();

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
                    new BuilderMapEntry(modifierBlock, parent.getBuilder(), entry.getValue()));
              });
    }

    // Create the ProtocolSchedule, such that the Dao/fork milestones can be inserted
    builders
        .values()
        .forEach(
            e -> addProtocolSpec(timestampSchedule, e.getTimestamp(), e.getBuilder(), e.modifier));

    LOG.info("Timestamp schedule created with milestones: {}", timestampSchedule.listMilestones());
    return timestampSchedule;
  }

  private void validatorEthereumForkOrdering() {
    long lastForkTimestamp = 0;
    lastForkTimestamp =
        validateForkOrder("Shanghai", config.getShanghaiTimestamp(), lastForkTimestamp);
    lastForkTimestamp = validateForkOrder("Cancun", config.getCancunTimestamp(), lastForkTimestamp);
    assert (lastForkTimestamp >= 0);
  }

  private long validateForkOrder(
      final String forkName, final OptionalLong thisForkTimestamp, final long lastForkTimestamp) {
    final long referenceForkTimestamp = thisForkTimestamp.orElse(lastForkTimestamp);
    if (lastForkTimestamp > referenceForkTimestamp) {
      throw new RuntimeException(
          String.format(
              "Genesis Config Error: '%s' is scheduled for timestamp %d but it must be on or after timestamp %d.",
              forkName, thisForkTimestamp.getAsLong(), lastForkTimestamp));
    }
    return referenceForkTimestamp;
  }

  private TreeMap<Long, BuilderMapEntry> buildMilestoneMap(
      final MainnetProtocolSpecFactory specFactory) {
    return Stream.of(
            // generally this TimestampSchedule will not have an entry for 0 instead it is relying
            // on defaulting to a MergeProtocolSchedule in
            // TransitionProtocolSchedule.getByBlockHeader if the given timestamp is before the
            // first entry in TimestampSchedule
            create(config.getShanghaiTimestamp(), specFactory.shanghaiDefinition(config)),
            create(config.getCancunTimestamp(), specFactory.cancunDefinition(config)))
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
      final DefaultTimestampSchedule protocolSchedule,
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
