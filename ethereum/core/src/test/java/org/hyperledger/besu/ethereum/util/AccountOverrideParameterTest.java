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
package org.hyperledger.besu.ethereum.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class AccountOverrideParameterTest {

  @SuppressWarnings("unchecked")
  @Test
  public void jsonDeserializesCorrectly() throws Exception {
    final String json =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{"
            + "\"from\":\"0x0\", \"to\": \"0x0\"}, "
            + "\"latest\","
            + "{\"0xd9c9cd5f6779558b6e0ed4e6acf6b1947e7fa1f3\":"
            + "{"
            + "\"balance\": \"0x01\","
            + "\"nonce\": 88"
            + "}}],\"id\":1}";

    final JsonRpcRequestContext request = new JsonRpcRequestContext(readJsonAsJsonRpcRequest(json));
    JsonCallParameter p = request.getRequiredParameter(0, JsonCallParameter.class);
    System.out.println(p);

    Object o = request.getRequiredParameter(2, HashMap.class);
    assertThat(o).isInstanceOf(Map.class);
    final Map<String, String> parsedParameter = (Map<String, String>) o;
    System.out.println("map" + parsedParameter);
    final AccountOverrideMap accountOverrideParam =
        request.getRequiredParameter(2, AccountOverrideMap.class);
    System.out.println(accountOverrideParam);

    //    assertThat(parsedParameter.get("balance")).isEqualTo("0x01");

    assertThat(
            accountOverrideParam
                .get(Address.fromHexString("0xd9c9cd5f6779558b6e0ed4e6acf6b1947e7fa1f3"))
                .getNonce())
        .isEqualTo(Optional.of(88L));
  }

  private JsonRpcRequest readJsonAsJsonRpcRequest(final String json) throws java.io.IOException {
    return new ObjectMapper().readValue(json, JsonRpcRequest.class);
  }
}
