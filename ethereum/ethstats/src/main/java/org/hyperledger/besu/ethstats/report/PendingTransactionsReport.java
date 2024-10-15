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
 * This interface represents a pending transactions report. It provides methods to get the id and
 * stats of the pending transactions report.
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, depluralize = true)
@JsonSerialize(as = ImmutablePendingTransactionsReport.class)
@JsonDeserialize(as = ImmutablePendingTransactionsReport.class)
public interface PendingTransactionsReport {

  /**
   * Gets the id of the pending transactions report.
   *
   * @return the id of the pending transactions report.
   */
  @JsonProperty("id")
  String getId();

  /**
   * Gets the stats of the pending transactions report.
   *
   * @return the stats of the pending transactions report.
   */
  @JsonProperty("stats")
  PStats getStats();

  /** This interface represents the stats of a pending transactions report. */
  @Value.Immutable
  @Value.Style(allParameters = true)
  @JsonSerialize(as = ImmutablePStats.class)
  @JsonDeserialize(as = ImmutablePStats.class)
  interface PStats {

    /**
     * Gets the number of pending transactions.
     *
     * @return the number of pending transactions.
     */
    @JsonProperty("pending")
    int getPending();
  }
}
