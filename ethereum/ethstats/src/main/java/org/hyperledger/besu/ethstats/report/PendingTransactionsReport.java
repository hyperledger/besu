/*
 * Copyright (C) 2008 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.hyperledger.besu.ethstats.report;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PendingTransactionsReport {

  @JsonProperty("id")
  private final String id;

  @JsonProperty("stats")
  private final Stats stats;

  @JsonCreator
  public PendingTransactionsReport(final String id, final int pending) {
    this.id = id;
    this.stats = new Stats(pending);
  }

  public String getId() {
    return id;
  }

  public Stats getStats() {
    return stats;
  }

  static class Stats {

    @JsonProperty("pending")
    private final int pending;

    public Stats(final int pending) {
      this.pending = pending;
    }

    public int getPending() {
      return pending;
    }
  }
}
