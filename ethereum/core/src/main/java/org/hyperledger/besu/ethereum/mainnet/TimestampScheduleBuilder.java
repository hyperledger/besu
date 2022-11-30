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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimestampScheduleBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(TimestampScheduleBuilder.class);
  private final GenesisConfigOptions config;
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
        privacyParameters,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration);
  }

  public TimestampScheduleBuilder(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    this(
        config,
        Optional.empty(),
        privacyParameters,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration);
  }

  private TimestampScheduleBuilder(
      final GenesisConfigOptions config,
      final Optional<BigInteger> defaultChainId,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    this.config = config;
    this.defaultChainId = defaultChainId;
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
    return Optional.of(new BuilderMapEntry(timestamp, builder));
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

    final TreeMap<Long, BuilderMapEntry> builders = buildMilestoneMap(specFactory);

    // Create the ProtocolSchedule, such that the Dao/fork milestones can be inserted
    builders
        .values()
        .forEach(e -> addProtocolSpec(timestampSchedule, e.getTimestamp(), e.getBuilder()));

    LOG.info("Timestamp schedule created with milestones: {}", timestampSchedule.listMilestones());
    return timestampSchedule;
  }

  private TreeMap<Long, BuilderMapEntry> buildMilestoneMap(
      final MainnetProtocolSpecFactory specFactory) {
    return Stream.of(create(config.getShanghaiTimestamp(), specFactory.shanghaiDefinition(config)))
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
      final ProtocolSpecBuilder definition) {
    definition
        .badBlocksManager(badBlockManager)
        .privacyParameters(privacyParameters)
        .privateTransactionValidatorBuilder(
            () -> new PrivateTransactionValidator(protocolSchedule.getChainId()));

    protocolSchedule.putMilestone(timestamp, definition.build(protocolSchedule));
  }

  private static class BuilderMapEntry {

    private final long timestamp;
    private final ProtocolSpecBuilder builder;

    public BuilderMapEntry(final long timestamp, final ProtocolSpecBuilder builder) {
      this.timestamp = timestamp;
      this.builder = builder;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public ProtocolSpecBuilder getBuilder() {
      return builder;
    }
  }
}
