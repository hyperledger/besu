/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.debug.TraceOptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionTraceParams {

  private final boolean disableStorage;
  private final boolean disableMemory;
  private final boolean disableStack;

  @JsonCreator()
  public TransactionTraceParams(
      @JsonProperty("disableStorage") final boolean disableStorage,
      @JsonProperty("disableMemory") final boolean disableMemory,
      @JsonProperty("disableStack") final boolean disableStack) {
    this.disableStorage = disableStorage;
    this.disableMemory = disableMemory;
    this.disableStack = disableStack;
  }

  public TraceOptions traceOptions() {
    return new TraceOptions(!disableStorage, !disableMemory, !disableStack);
  }
}
