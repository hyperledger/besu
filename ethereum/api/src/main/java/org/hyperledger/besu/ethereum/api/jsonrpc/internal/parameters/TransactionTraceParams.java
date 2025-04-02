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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.debug.CallTracerConfig;
import org.hyperledger.besu.ethereum.debug.OpcodeTracerConfig;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.debug.TracerConfig;
import org.hyperledger.besu.ethereum.debug.TracerType;

import java.util.LinkedHashMap;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableTransactionTraceParams.class)
@JsonDeserialize(as = ImmutableTransactionTraceParams.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface TransactionTraceParams {

  @JsonProperty("txHash")
  @Nullable
  String getTransactionHash();

  @JsonProperty(value = "disableStorage")
  @Value.Default
  default boolean disableStorage() {
    return false;
  }

  @JsonProperty(value = "disableMemory")
  @Value.Default
  default boolean disableMemory() {
    return false;
  }

  @JsonProperty(value = "disableStack")
  @Value.Default
  default boolean disableStack() {
    return false;
  }

  @JsonProperty("tracer")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Nullable
  String tracer();

  @JsonProperty("tracerConfig")
  @Nullable
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @SuppressWarnings("NonApiType") // to allow for LinkedHashMap instead of default Guava Map
  LinkedHashMap<String, Boolean> tracerConfig();

  /**
   * Convert JSON-RPC parameters to a {@link TraceOptions} object.
   *
   * @return TraceOptions object containing the tracer type and configuration.
   */
  default TraceOptions<? extends TracerConfig> traceOptions() {
    var tracerType = TracerType.fromString(tracer());
    var tracerConfig =
        switch (tracerType) {
          case DEFAULT ->
              new OpcodeTracerConfig(!disableStorage(), !disableMemory(), !disableStack());
          case CALL_TRACER, FLAT_CALL_TRACER -> {
            var tracerConfigMap = tracerConfig();
            if (tracerConfigMap == null) {
              // TODO: Confirm geth defaults
              yield new CallTracerConfig(true, true);
            } else {
              var onlyTopCall = tracerConfigMap.getOrDefault("onlyTopCall", true);
              var withLog = tracerConfigMap.getOrDefault("withLog", true);

              yield new CallTracerConfig(
                  onlyTopCall != null ? onlyTopCall : true, withLog != null ? withLog : true);
            }
          }
        };
    return new TraceOptions<>(tracerType, tracerConfig);
  }
}
