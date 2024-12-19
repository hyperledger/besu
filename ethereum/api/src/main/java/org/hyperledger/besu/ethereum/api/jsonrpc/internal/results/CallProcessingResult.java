/*
 * Copyright contributors to Besu.
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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

public class CallProcessingResult {
  @JsonProperty("status")
  private final String status;

  @JsonProperty("returnData")
  private final String returnData;

  @JsonProperty("gasUsed")
  private final String gasUsed;

  @JsonProperty("error")
  private final ErrorDetails error;

  @JsonProperty("logs")
  private final List<LogResult> logs;

  public CallProcessingResult(
      @JsonProperty("status") final int status,
      @JsonProperty("returnData") final Bytes returnData,
      @JsonProperty("gasUsed") final long gasUsed,
      @JsonProperty("error") final ErrorDetails error,
      @JsonProperty("logs") final List<LogResult> logs) {
    this.status = Quantity.create(status);
    this.returnData = returnData.toString();

    this.gasUsed = Quantity.create(gasUsed);
    this.error = error;
    this.logs = logs;
  }

  public String getStatus() {
    return status;
  }

  public String getReturnData() {
    return returnData;
  }

  public String getGasUsed() {
    return gasUsed;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public ErrorDetails getError() {
    return error;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public List<LogResult> getLogs() {
    return logs;
  }

  public record ErrorDetails(
      @JsonProperty("code") long code,
      @JsonProperty("message") String message,
      @JsonProperty("data") Bytes data) {

    @Override
    public long code() {
      return code;
    }

    @Override
    public String message() {
      return message;
    }

    @Override
    public Bytes data() {
      return data;
    }
  }
}
