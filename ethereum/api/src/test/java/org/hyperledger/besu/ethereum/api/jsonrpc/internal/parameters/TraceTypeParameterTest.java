/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class TraceTypeParameterTest {

  @Test
  public void jsonWithArrayOfTraceTypesShouldSerializeSuccessfully() throws Exception {
    final String jsonWithTraceTypes =
        "{\"jsonrpc\":\"2.0\",\"method\":\"trace_rawTransaction\",\"params\":[\"0xf86f\", [\"trace\",\"stateDiff\"]],\"id\":1}";
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(readJsonAsJsonRpcRequest(jsonWithTraceTypes));
    final TraceTypeParameter expectedTraceTypeParam =
        new TraceTypeParameter(List.of("trace", "stateDiff"));

    final TraceTypeParameter parsedTraceTypeParam =
        request.getRequiredParameter(1, TraceTypeParameter.class);

    assertThat(parsedTraceTypeParam).usingRecursiveComparison().isEqualTo(expectedTraceTypeParam);
    final String traceTypeString = parsedTraceTypeParam.toString();
    assertThat(traceTypeString).contains(TraceTypeParameter.TraceType.TRACE.toString());
    assertThat(traceTypeString).contains(TraceTypeParameter.TraceType.STATE_DIFF.toString());
  }

  private JsonRpcRequest readJsonAsJsonRpcRequest(final String json) throws java.io.IOException {
    return new ObjectMapper().readValue(json, JsonRpcRequest.class);
  }
}
