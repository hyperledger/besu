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
package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class TrailingPeerRequirements {
  public static TrailingPeerRequirements UNRESTRICTED =
      new TrailingPeerRequirements(BlockHeader.GENESIS_BLOCK_NUMBER, Long.MAX_VALUE);
  private final long minimumHeightToBeUpToDate;
  private final long maxTrailingPeers;

  public TrailingPeerRequirements(
      final long minimumHeightToBeUpToDate, final long maxTrailingPeers) {
    this.minimumHeightToBeUpToDate = minimumHeightToBeUpToDate;
    this.maxTrailingPeers = maxTrailingPeers;
  }

  public long getMinimumHeightToBeUpToDate() {
    return minimumHeightToBeUpToDate;
  }

  public long getMaxTrailingPeers() {
    return maxTrailingPeers;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TrailingPeerRequirements that = (TrailingPeerRequirements) o;
    return minimumHeightToBeUpToDate == that.minimumHeightToBeUpToDate
        && maxTrailingPeers == that.maxTrailingPeers;
  }

  @Override
  public int hashCode() {
    return Objects.hash(minimumHeightToBeUpToDate, maxTrailingPeers);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("minimumHeightToBeUpToDate", minimumHeightToBeUpToDate)
        .add("maxTrailingPeers", maxTrailingPeers)
        .toString();
  }

  /**
   * Calculate a reasonable value for the maximum number of trailing peers to allow while behind the
   * chain.
   *
   * <p>The number of peers is restricted to ensure we have room for peers ahead of us on the chain
   * to connect and to limit the amount of work required to support requests from trailing peers
   * while we're working to catch up.
   *
   * @param maxPeers the overall peer limit.
   * @return the number of trailing peers allowed while catching up to the best peer.
   */
  public static int calculateMaxTrailingPeers(final int maxPeers) {
    return Math.max((int) Math.ceil(maxPeers * 0.25), 1);
  }
}
