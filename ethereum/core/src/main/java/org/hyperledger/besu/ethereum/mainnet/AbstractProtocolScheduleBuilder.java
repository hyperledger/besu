/*
 * Copyright Hyperledger Besu Contributors.
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
import org.hyperledger.besu.ethereum.linea.LineaParameters;
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

public abstract class AbstractProtocolScheduleBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractProtocolScheduleBuilder.class);
  protected final GenesisConfigOptions config;
  protected final ProtocolSpecAdapters protocolSpecAdapters;
  protected final PrivacyParameters privacyParameters;
  protected final boolean isRevertReasonEnabled;
  protected final BadBlockManager badBlockManager = new BadBlockManager();
  protected final EvmConfiguration evmConfiguration;
  protected final LineaParameters lineaParameters;

  protected AbstractProtocolScheduleBuilder(
      final GenesisConfigOptions config,
      final ProtocolSpecAdapters protocolSpecAdapters,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration,
      final LineaParameters lineaParameters) {
    this.config = config;
    this.protocolSpecAdapters = protocolSpecAdapters;
    this.privacyParameters = privacyParameters;
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    this.evmConfiguration = evmConfiguration;
    this.lineaParameters = lineaParameters;
  }

  protected void initSchedule(
      final HeaderBasedProtocolSchedule protocolSchedule, final Optional<BigInteger> chainId) {

    final MainnetProtocolSpecFactory specFactory =
        new MainnetProtocolSpecFactory(
            chainId,
            config.getContractSizeLimit(),
            config.getEvmStackSize(),
            isRevertReasonEnabled,
            config.getEcip1017EraRounds(),
            evmConfiguration);

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
                    new BuilderMapEntry(modifierBlock, parent.getBuilder(), entry.getValue()));
              });
    }

    // Create the ProtocolSchedule, such that the Dao/fork milestones can be inserted
    builders
        .values()
        .forEach(
            e ->
                addProtocolSpec(
                    protocolSchedule, e.getBlockIdentifier(), e.getBuilder(), e.modifier));

    postBuildStep(specFactory, builders);

    LOG.info("Protocol schedule created with milestones: {}", protocolSchedule.listMilestones());
  }

  abstract void validateForkOrdering();

  protected long validateForkOrder(
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

  abstract Stream<Optional<BuilderMapEntry>> createMilestones(
      final MainnetProtocolSpecFactory specFactory);

  protected Optional<BuilderMapEntry> create(
      final OptionalLong blockIdentifier, final ProtocolSpecBuilder builder) {
    if (blockIdentifier.isEmpty()) {
      return Optional.empty();
    }
    final long blockVal = blockIdentifier.getAsLong();
    return Optional.of(
        new BuilderMapEntry(blockVal, builder, protocolSpecAdapters.getModifierForBlock(blockVal)));
  }

  protected ProtocolSpec getProtocolSpec(
      final HeaderBasedProtocolSchedule protocolSchedule,
      final ProtocolSpecBuilder definition,
      final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier) {
    definition
        .badBlocksManager(badBlockManager)
        .privacyParameters(privacyParameters)
        .privateTransactionValidatorBuilder(
            () -> new PrivateTransactionValidator(protocolSchedule.getChainId()));

    return modifier.apply(definition).build(protocolSchedule);
  }

  protected void addProtocolSpec(
      final HeaderBasedProtocolSchedule protocolSchedule,
      final long blockNumberOrTimestamp,
      final ProtocolSpecBuilder definition,
      final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier) {

    protocolSchedule.putMilestone(
        blockNumberOrTimestamp, getProtocolSpec(protocolSchedule, definition, modifier));
  }

  abstract void postBuildStep(
      final MainnetProtocolSpecFactory specFactory, final TreeMap<Long, BuilderMapEntry> builders);

  protected static class BuilderMapEntry {

    private final long blockIdentifier;
    private final ProtocolSpecBuilder builder;
    private final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier;

    public BuilderMapEntry(
        final long blockIdentifier,
        final ProtocolSpecBuilder builder,
        final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier) {
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
  }
}
