/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public interface ProtocolSchedule extends PrivacySupportingProtocolSchedule {

  ProtocolSpec getByBlockHeader(final ProcessableBlockHeader blockHeader);

  default ProtocolSpec getForNextBlockHeader(
      final BlockHeader parentBlockHeader, final long timestampForNextBlock) {
    final BlockHeader nextBlockHeader =
        BlockHeaderBuilder.fromHeader(parentBlockHeader)
            .number(parentBlockHeader.getNumber() + 1)
            .timestamp(timestampForNextBlock)
            .parentHash(parentBlockHeader.getHash())
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .buildBlockHeader();
    return getByBlockHeader(nextBlockHeader);
  }

  Optional<BigInteger> getChainId();

  String listMilestones();

  void putBlockNumberMilestone(final long blockNumber, final ProtocolSpec protocolSpec);

  void putTimestampMilestone(final long timestamp, final ProtocolSpec protocolSpec);

  default void setMilestones(final Map<HardforkId, Long> milestoneList) {
    throw new UnsupportedOperationException("Not implemented");
  }

  default Optional<ScheduledProtocolSpec.Hardfork> hardforkFor(
      final Predicate<ScheduledProtocolSpec> predicate) {
    throw new UnsupportedOperationException("Not implemented");
  }

  default Optional<Long> milestoneFor(final HardforkId hardforkId) {
    throw new UnsupportedOperationException("Not implemented");
  }

  boolean isOnMilestoneBoundary(final BlockHeader blockHeader);

  boolean anyMatch(Predicate<ScheduledProtocolSpec> predicate);
}
