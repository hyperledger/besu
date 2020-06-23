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

public abstract class AbstractBlockScheduler {

  protected final Clock clock;

  protected AbstractBlockScheduler(final Clock clock) {
    this.clock = clock;
  }

  public long waitUntilNextBlockCanBeMined(final BlockHeader parentHeader)
      throws InterruptedException {
    final BlockCreationTimeResult result = getNextTimestamp(parentHeader);

    Thread.sleep(result.millisecondsUntilValid);

    return result.timestampForHeader;
  }

  public abstract BlockCreationTimeResult getNextTimestamp(final BlockHeader parentHeader);

  public static class BlockCreationTimeResult {

    private final long timestampForHeader;
    private final long millisecondsUntilValid;

    public BlockCreationTimeResult(
        final long timestampForHeader, final long millisecondsUntilValid) {
      this.timestampForHeader = timestampForHeader;
      this.millisecondsUntilValid = millisecondsUntilValid;
    }

    public long getTimestampForHeader() {
      return timestampForHeader;
    }

    public long getMillisecondsUntilValid() {
      return millisecondsUntilValid;
    }
  }
}
