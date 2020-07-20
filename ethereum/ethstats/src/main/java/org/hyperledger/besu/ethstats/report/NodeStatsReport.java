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
package org.hyperledger.besu.ethstats.report;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeStatsReport {

  @JsonProperty("id")
  private final String id;

  @JsonProperty("stats")
  private final Stats stats;

  @JsonCreator
  public NodeStatsReport(final String id, final Stats stats) {
    this.id = id;
    this.stats = stats;
  }

  public String getId() {
    return id;
  }

  public Stats getStats() {
    return stats;
  }

  public static class Stats {

    @JsonProperty("active")
    private final boolean active;

    @JsonProperty("mining")
    private final boolean mining;

    @JsonProperty("hashrate")
    private final long hashrate;

    @JsonProperty("peers")
    private final int peers;

    @JsonProperty("gasPrice")
    private final long gasPrice;

    @JsonProperty("syncing")
    private final boolean syncing;

    @JsonProperty("upTime")
    private final int upTime;

    public Stats(
        final boolean active,
        final boolean mining,
        final long hashrate,
        final int peers,
        final long gasPrice,
        final boolean syncing,
        final int upTime) {
      this.active = active;
      this.mining = mining;
      this.hashrate = hashrate;
      this.peers = peers;
      this.gasPrice = gasPrice;
      this.syncing = syncing;
      this.upTime = upTime;
    }

    public boolean isActive() {
      return active;
    }

    public boolean isMining() {
      return mining;
    }

    public long getHashrate() {
      return hashrate;
    }

    public int getPeers() {
      return peers;
    }

    public long getGasPrice() {
      return gasPrice;
    }

    public boolean isSyncing() {
      return syncing;
    }

    public int getUpTime() {
      return upTime;
    }
  }
}
