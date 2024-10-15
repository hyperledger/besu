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

import static java.util.Objects.isNull;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;

public class TraceTypeParameter {

  public enum TraceType {
    TRACE,
    VM_TRACE,
    STATE_DIFF;

    @JsonCreator
    static TraceType fromString(final String traceType) {
      if (traceType.equalsIgnoreCase("trace")) {
        return TraceType.TRACE;
      }
      if (traceType.equalsIgnoreCase("vmTrace")) {
        return TraceType.VM_TRACE;
      }
      if (traceType.equalsIgnoreCase("stateDiff")) {
        return TraceType.STATE_DIFF;
      }
      return null;
    }
  }

  private final Set<TraceType> traceTypes;

  @JsonCreator
  public TraceTypeParameter(final List<String> traceTypesInput) {
    validateTraceTypes(traceTypesInput);

    this.traceTypes =
        traceTypesInput.stream()
            .map(TraceType::fromString)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
  }

  public Set<TraceType> getTraceTypes() {
    return traceTypes;
  }

  private void validateTraceTypes(final List<String> traceTypesInput)
      throws InvalidJsonRpcParameters {
    // Make sure no unsupported types were included
    final String unsupportedTypes =
        traceTypesInput.stream()
            .filter(strVal -> isNull(TraceType.fromString(strVal)))
            .collect(Collectors.joining(", "));

    if (!unsupportedTypes.isEmpty()) {
      throw new InvalidJsonRpcParameters(
          "Invalid trace types supplied: " + unsupportedTypes,
          RpcErrorType.INVALID_TRACE_TYPE_PARAMS);
    }
  }

  @Override
  public String toString() {
    return "TraceTypeParameter" + getTraceTypes().stream().collect(Collectors.toList());
  }
}
