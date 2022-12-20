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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.ethereum.vm.LogMock;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

/**
 * A VM test case specification.
 *
 * <p>Note: this class will be auto-generated with the JSON test specification.
 */
@JsonIgnoreProperties("_info")
public class LogsBloomFilterTestCaseSpec {

  public LogsBloomFilter logsBloomFilter;

  public LogsBloomFilter finalBloom;

  public List<LogMock> logs;

  /** Public constructor. */
  @JsonCreator
  public LogsBloomFilterTestCaseSpec(
      @JsonProperty("logs") final List<LogMock> logs,
      @JsonProperty("bloom") final String finalBloom) {
    this.logs = logs;
    this.finalBloom = new LogsBloomFilter(Bytes.fromHexString(finalBloom));
  }

  public List<LogMock> getLogs() {
    return logs;
  }

  /**
   * @return - 2048-bit representation of each log entry, except data, of each transaction.
   */
  public LogsBloomFilter getLogsBloomFilter() {
    return logsBloomFilter;
  }

  public LogsBloomFilter getFinalBloom() {
    return finalBloom;
  }
}
