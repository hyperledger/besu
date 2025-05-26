/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.mainnet.milestones;

import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;

import java.util.OptionalLong;
import java.util.function.Supplier;

/** Configuration for a milestone definition. */
public class MilestoneDefinition {
  private final HardforkId hardforkId;
  private final OptionalLong blockNumberOrTimestamp;
  private final Supplier<ProtocolSpecBuilder> specBuilder;
  private final MilestoneType milestoneType;

  /**
   * Constructs a MilestoneDefinitionConfig.
   *
   * @param hardforkId the hardfork identifier
   * @param blockNumberOrTimestamp the block number or timestamp for the milestone
   * @param specBuilder the protocol specification builder for the milestone
   * @param milestoneType the type of milestone (block number or timestamp)
   */
  private MilestoneDefinition(
      final HardforkId hardforkId,
      final OptionalLong blockNumberOrTimestamp,
      final Supplier<ProtocolSpecBuilder> specBuilder,
      final MilestoneType milestoneType) {
    this.hardforkId = hardforkId;
    this.blockNumberOrTimestamp = blockNumberOrTimestamp;
    this.specBuilder = specBuilder;
    this.milestoneType = milestoneType;
  }

  static MilestoneDefinition createBlockNumberMilestone(
      final HardforkId hardforkId,
      final OptionalLong blockNumber,
      final Supplier<ProtocolSpecBuilder> specBuilder) {
    return new MilestoneDefinition(
        hardforkId, blockNumber, specBuilder, MilestoneType.BLOCK_NUMBER);
  }

  static MilestoneDefinition createTimestampMilestone(
      final HardforkId hardforkId,
      final OptionalLong timestamp,
      final Supplier<ProtocolSpecBuilder> specBuilder) {
    return new MilestoneDefinition(hardforkId, timestamp, specBuilder, MilestoneType.TIMESTAMP);
  }

  public HardforkId getHardforkId() {
    return hardforkId;
  }

  public OptionalLong getBlockNumberOrTimestamp() {
    return blockNumberOrTimestamp;
  }

  public Supplier<ProtocolSpecBuilder> getSpecBuilder() {
    return specBuilder;
  }

  public MilestoneType getMilestoneType() {
    return milestoneType;
  }
}
