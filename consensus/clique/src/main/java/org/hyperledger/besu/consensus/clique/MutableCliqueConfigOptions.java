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
package org.hyperledger.besu.consensus.clique;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.CliqueConfigOptions;
import org.hyperledger.besu.consensus.common.ForksSchedule;

import java.util.Map;

/**
 * A mutable {@link BftConfigOptions} that is used for building config for transitions in the {@link
 * ForksSchedule}*.
 */
public class MutableCliqueConfigOptions implements CliqueConfigOptions {
  private long epochLength;
  private int blockPeriodSeconds;

  /**
   * Instantiates a new Mutable bft config options.
   *
   * @param bftConfigOptions the bft config options
   */
  public MutableCliqueConfigOptions(final CliqueConfigOptions bftConfigOptions) {
    this.epochLength = bftConfigOptions.getEpochLength();
    this.blockPeriodSeconds = bftConfigOptions.getBlockPeriodSeconds();
  }

  @Override
  public long getEpochLength() {
    return epochLength;
  }

  @Override
  public int getBlockPeriodSeconds() {
    return blockPeriodSeconds;
  }

  @Override
  public Map<String, Object> asMap() {
    return Map.of();
  }

  /**
   * Sets epoch length.
   *
   * @param epochLength the epoch length
   */
  public void setEpochLength(final long epochLength) {
    this.epochLength = epochLength;
  }

  /**
   * Sets block period seconds.
   *
   * @param blockPeriodSeconds the block period seconds
   */
  public void setBlockPeriodSeconds(final int blockPeriodSeconds) {
    this.blockPeriodSeconds = blockPeriodSeconds;
  }
}
