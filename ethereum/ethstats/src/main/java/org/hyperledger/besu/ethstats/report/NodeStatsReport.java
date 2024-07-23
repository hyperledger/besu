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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * This interface represents a node stats report. It provides methods to get the id and stats of the
 * node stats report.
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, depluralize = true)
@JsonSerialize(as = ImmutableNodeStatsReport.class)
@JsonDeserialize(as = ImmutableNodeStatsReport.class)
public interface NodeStatsReport {

  /**
   * Gets the id of the node stats report.
   *
   * @return the id of the node stats report.
   */
  @JsonProperty("id")
  String getId();

  /**
   * Gets the stats of the node stats report.
   *
   * @return the stats of the node stats report.
   */
  @JsonProperty("stats")
  NStats getStats();

  /** This interface represents the stats of a node. */
  @Value.Immutable
  @Value.Style(allParameters = true)
  @JsonSerialize(as = ImmutableNStats.class)
  @JsonDeserialize(as = ImmutableNStats.class)
  interface NStats {

    /**
     * Checks if the node is active.
     *
     * @return true if the node is active, false otherwise.
     */
    @JsonProperty("active")
    boolean isActive();

    /**
     * Checks if the node is mining.
     *
     * @return true if the node is mining, false otherwise.
     */
    @JsonProperty("mining")
    boolean isMining();

    /**
     * Gets the hashrate of the node.
     *
     * @return the hashrate of the node.
     */
    @JsonProperty("hashrate")
    long getHashrate();

    /**
     * Gets the number of peers of the node.
     *
     * @return the number of peers of the node.
     */
    @JsonProperty("peers")
    int getPeers();

    /**
     * Gets the gas price of the node.
     *
     * @return the gas price of the node.
     */
    @JsonProperty("gasPrice")
    long getGasPrice();

    /**
     * Checks if the node is syncing.
     *
     * @return true if the node is syncing, false otherwise.
     */
    @JsonProperty("syncing")
    boolean isSyncing();

    /**
     * Gets the uptime of the node.
     *
     * @return the uptime of the node.
     */
    @JsonProperty("uptime")
    int getUpTime();
  }
}
