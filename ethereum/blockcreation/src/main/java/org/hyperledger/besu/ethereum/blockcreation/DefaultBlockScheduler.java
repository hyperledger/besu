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
package org.hyperledger.besu.ethereum.blockcreation;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.function.ToLongFunction;

import com.google.common.annotations.VisibleForTesting;

public class DefaultBlockScheduler extends AbstractBlockScheduler {

  private final long acceptableClockDriftSeconds;
  private final ToLongFunction<BlockHeader> calcMinimumSecondsSinceParent;

  public DefaultBlockScheduler(
      final long calcMinimumSecondsSinceParent,
      final long acceptableClockDriftSeconds,
      final Clock clock) {
    this((bh) -> calcMinimumSecondsSinceParent, acceptableClockDriftSeconds, clock);
  }

  protected DefaultBlockScheduler(
      final ToLongFunction<BlockHeader> calcMinimumSecondsSinceParent,
      final long acceptableClockDriftSeconds,
      final Clock clock) {
    super(clock);
    this.acceptableClockDriftSeconds = acceptableClockDriftSeconds;
    this.calcMinimumSecondsSinceParent = calcMinimumSecondsSinceParent;
  }

  @Override
  @VisibleForTesting
  public BlockCreationTimeResult getNextTimestamp(final BlockHeader parentHeader) {
    final long msSinceEpoch = clock.millis();
    final long now = TimeUnit.SECONDS.convert(msSinceEpoch, TimeUnit.MILLISECONDS);
    final long parentTimestamp = parentHeader.getTimestamp();

    final long minSecondsSinceParent = calcMinimumSecondsSinceParent.applyAsLong(parentHeader);
    final long nextHeaderTimestamp = Long.max(parentTimestamp + minSecondsSinceParent, now);

    final long earliestBlockTransmissionTime = nextHeaderTimestamp - acceptableClockDriftSeconds;
    final long msUntilBlockTransmission = (earliestBlockTransmissionTime * 1000) - msSinceEpoch;

    return new BlockCreationTimeResult(nextHeaderTimestamp, Math.max(0, msUntilBlockTransmission));
  }
}
