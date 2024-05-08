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

/** The type Abstract block scheduler. */
public abstract class AbstractBlockScheduler {

  /** The Clock. */
  protected final Clock clock;

  /**
   * Instantiates a new Abstract block scheduler.
   *
   * @param clock the clock
   */
  protected AbstractBlockScheduler(final Clock clock) {
    this.clock = clock;
  }

  /**
   * Wait until next block can be mined long.
   *
   * @param parentHeader the parent header
   * @return the long
   * @throws InterruptedException the interrupted exception
   */
  public long waitUntilNextBlockCanBeMined(final BlockHeader parentHeader)
      throws InterruptedException {
    final BlockCreationTimeResult result = getNextTimestamp(parentHeader);

    Thread.sleep(result.millisecondsUntilValid);

    return result.timestampForHeader;
  }

  /**
   * Gets next timestamp.
   *
   * @param parentHeader the parent header
   * @return the next timestamp
   */
  public abstract BlockCreationTimeResult getNextTimestamp(final BlockHeader parentHeader);

  /**
   * The type Block creation time result.
   *
   * @param timestampForHeader the timestamp for header
   * @param millisecondsUntilValid the milliseconds until valid
   */
  public record BlockCreationTimeResult(long timestampForHeader, long millisecondsUntilValid) {}
}
