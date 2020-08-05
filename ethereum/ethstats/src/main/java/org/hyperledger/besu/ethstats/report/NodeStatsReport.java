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

@Value.Immutable
@Value.Style(deepImmutablesDetection = true, depluralize = true)
@JsonSerialize(as = ImmutableNodeStatsReport.class)
@JsonDeserialize(as = ImmutableNodeStatsReport.class)
public interface NodeStatsReport {

  @JsonProperty("id")
  String getId();

  @JsonProperty("stats")
  NStats getStats();

  @Value.Immutable
  @Value.Style(allParameters = true)
  @JsonSerialize(as = ImmutableNStats.class)
  @JsonDeserialize(as = ImmutableNStats.class)
  interface NStats {

    @JsonProperty("active")
    boolean isActive();

    @JsonProperty("mining")
    boolean isMining();

    @JsonProperty("hashrate")
    long getHashrate();

    @JsonProperty("peers")
    int getPeers();

    @JsonProperty("gasPrice")
    long getGasPrice();

    @JsonProperty("syncing")
    boolean isSyncing();

    @JsonProperty("uptime")
    int getUpTime();
  }
}
