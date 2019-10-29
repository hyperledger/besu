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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.ethereum.core.LogWithMetadata;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

/** The result set from querying the logs from one or more blocks. */
public class LogsResult {

  private final List<LogResult> results;

  public LogsResult(final List<LogWithMetadata> logs) {
    results = new ArrayList<>(logs.size());

    for (final LogWithMetadata log : logs) {
      results.add(new LogResult(log));
    }
  }

  @JsonValue
  public List<LogResult> getResults() {
    return results;
  }
}
