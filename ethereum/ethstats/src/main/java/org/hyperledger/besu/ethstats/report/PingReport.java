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
 * This interface represents a ping report. It provides methods to get the id and current time of
 * the ping report.
 */
@Value.Immutable
@Value.Style(allParameters = true)
@JsonSerialize(as = ImmutablePingReport.class)
@JsonDeserialize(as = ImmutablePingReport.class)
public interface PingReport {

  /**
   * Gets the id of the ping report.
   *
   * @return the id of the ping report.
   */
  @JsonProperty("id")
  String getId();

  /**
   * Gets the current time of the ping report.
   *
   * @return the current time of the ping report.
   */
  @JsonProperty("clientTime")
  String getCurrentTime();
}
