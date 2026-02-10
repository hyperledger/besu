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
package org.hyperledger.besu.consensus.common;

import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The Forks schedule.
 *
 * @param <C> the type parameter
 */
public class ForksSchedule<C> {

  private final NavigableSet<ForkSpec<C>> forks =
      new TreeSet<>(
          Comparator.comparing((Function<ForkSpec<C>, Long>) ForkSpec::getBlock).reversed());

  /**
   * Instantiates a new Forks schedule.
   *
   * @param forks the forks
   */
  public ForksSchedule(final Collection<ForkSpec<C>> forks) {
    this.forks.addAll(forks);
  }

  /**
   * Gets fork.
   *
   * @param blockNumber the block number
   * @return the fork
   */
  public ForkSpec<C> getFork(final long blockNumber, final long timestampSeconds, final ScheduledProtocolSpec.ScheduleType currentProtocolScheduleType) {
    for (final ForkSpec<C> f : forks) {
      if (currentProtocolScheduleType == ScheduledProtocolSpec.ScheduleType.BLOCK && blockNumber >= f.getBlock()) {
        System.out.println("Type: " + currentProtocolScheduleType + ", fork block: " + f.getBlock() + ", current block: " + blockNumber);
        //System.out.println("Block " + blockNumber + " is higher than fork " + f + " with fork number " + f.getBlock() + " - returning f");
        return f;
      }
      if (currentProtocolScheduleType == ScheduledProtocolSpec.ScheduleType.TIME && timestampSeconds >= f.getBlock()) {
        System.out.println("Type: " + currentProtocolScheduleType + ", fork time: " + f.getBlock() + ", current time: " + timestampSeconds);
        //System.out.println("Fork block " + f.getBlock() + " is higher than timestamp seconds " + timestampSeconds + " - returning f");
        return f;
      }
    }

    return forks.first();
  }

  /**
   * Gets forks.
   *
   * @return the forks
   */
  public Set<ForkSpec<C>> getForks() {
    return Collections.unmodifiableSet(forks);
  }
}
