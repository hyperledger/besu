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
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.Optional;
import java.util.stream.Stream;

public class TimestampScheduleBuilder extends AbstractProtocolScheduleBuilder {

  private final Optional<BigInteger> defaultChainId;

  public TimestampScheduleBuilder(
      final GenesisConfigOptions config,
      final BigInteger defaultChainId,
      final ProtocolSpecAdapters protocolSpecAdapters,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    super(
        config,
        protocolSpecAdapters,
        privacyParameters,
        isRevertReasonEnabled,
        quorumCompatibilityMode,
        evmConfiguration);
    this.defaultChainId = Optional.of(defaultChainId);
  }

  public TimestampSchedule createTimestampSchedule() {
    final Optional<BigInteger> chainId = config.getChainId().or(() -> defaultChainId);
    TimestampSchedule timestampSchedule = new DefaultTimestampSchedule(chainId);
    initSchedule(timestampSchedule, chainId);
    return timestampSchedule;
  }

  @Override
  protected void validateForkOrdering() {
    long lastForkTimestamp = 0;
    lastForkTimestamp = validateForkOrder("Shanghai", config.getShanghaiTime(), lastForkTimestamp);
    lastForkTimestamp = validateForkOrder("Cancun", config.getCancunTime(), lastForkTimestamp);
    lastForkTimestamp =
        validateForkOrder("FutureEips", config.getFutureEipsTime(), lastForkTimestamp);
    lastForkTimestamp =
        validateForkOrder("ExperimentalEips", config.getExperimentalEipsTime(), lastForkTimestamp);

    assert (lastForkTimestamp >= 0);
  }

  @Override
  protected String getBlockIdentifierName() {
    return "timestamp";
  }

  @Override
  protected Stream<Optional<BuilderMapEntry>> createMilestones(
      final MainnetProtocolSpecFactory specFactory) {
    return Stream.of(
        // generally this TimestampSchedule will not have an entry for 0 instead it is relying
        // on defaulting to a MergeProtocolSchedule in
        // TransitionProtocolSchedule.getByBlockHeader if the given timestamp is before the
        // first entry in TimestampSchedule
        create(config.getShanghaiTime(), specFactory.shanghaiDefinition(config)),
        create(config.getCancunTime(), specFactory.cancunDefinition(config)),
        create(config.getFutureEipsTime(), specFactory.futureEipsDefinition(config)),
        create(config.getExperimentalEipsTime(), specFactory.experimentalEipsDefinition(config)));
  }

  @Override
  protected void postBuildStep(final MainnetProtocolSpecFactory specFactory) {}
}
