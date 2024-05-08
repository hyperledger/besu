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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;

import java.util.ArrayList;
import java.util.List;

/** The type Log filter. */
class LogFilter extends Filter {

  private final BlockParameter fromBlock;
  private final BlockParameter toBlock;
  private final LogsQuery logsQuery;

  private final List<LogWithMetadata> logs = new ArrayList<>();

  /**
   * Instantiates a new Log filter.
   *
   * @param id the id
   * @param fromBlock the from block
   * @param toBlock the to block
   * @param logsQuery the logs query
   */
  LogFilter(
      final String id,
      final BlockParameter fromBlock,
      final BlockParameter toBlock,
      final LogsQuery logsQuery) {
    super(id);
    this.fromBlock = fromBlock;
    this.toBlock = toBlock;
    this.logsQuery = logsQuery;
  }

  /**
   * Gets from block.
   *
   * @return the from block
   */
  public BlockParameter getFromBlock() {
    return fromBlock;
  }

  /**
   * Gets to block.
   *
   * @return the to block
   */
  public BlockParameter getToBlock() {
    return toBlock;
  }

  /**
   * Gets logs query.
   *
   * @return the logs query
   */
  public LogsQuery getLogsQuery() {
    return logsQuery;
  }

  /**
   * Add logs.
   *
   * @param logs the logs
   */
  void addLogs(final List<LogWithMetadata> logs) {
    this.logs.addAll(logs);
  }

  /**
   * Logs list.
   *
   * @return the list
   */
  List<LogWithMetadata> logs() {
    return logs;
  }

  /** Clear logs. */
  void clearLogs() {
    logs.clear();
  }
}
