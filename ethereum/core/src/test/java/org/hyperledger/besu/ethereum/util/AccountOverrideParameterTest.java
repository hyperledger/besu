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
package org.hyperledger.besu.ethereum.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.datatypes.AccountOverride;
import org.hyperledger.besu.datatypes.AccountOverrideMap;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class AccountOverrideParameterTest {

  private static final String ADDRESS_HEX1 = "0xd9c9cd5f6779558b6e0ed4e6acf6b1947e7fa1f3";
  private static final String ADDRESS_HEX2 = "0xd5E23607D5d73ff2293152f464C3caB005f87696";
  private static final String STORAGE_KEY =
      "0x1cf7945003fc5b59d2f6736f0704557aa805c4f2844084ccd1173b8d56946962";
  private static final String STORAGE_VALUE =
      "0x000000000000000000000000000000000000000000000000000000110ed03bf7";
  private static final String CODE_STRING =
      "0xdbf4257000000000000000000000000000000000000000000000000000000000";

  @Test
  public void jsonDeserializesCorrectly() throws Exception {
    final String json =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{"
            + "\"from\":\"0x0\", \"to\": \"0x0\"}, "
            + "\"latest\","
            + "{\""
            + ADDRESS_HEX1
            + "\":"
            + "{"
            + "\"balance\": \"0x01\","
            + "\"nonce\": 88"
            + "}}],\"id\":1}";

    final JsonRpcRequestContext request = new JsonRpcRequestContext(readJsonAsJsonRpcRequest(json));
    final AccountOverrideMap accountOverrideParam =
        request.getRequiredParameter(2, AccountOverrideMap.class);

    final AccountOverride accountOverride =
        accountOverrideParam.get(Address.fromHexString(ADDRESS_HEX1));

    assertThat(accountOverride.getNonce()).isEqualTo(Optional.of(88L));
    assertThat(accountOverride.getBalance()).isEqualTo(Optional.of(Wei.of(1)));
    assertFalse(accountOverride.getStateDiff().isPresent());
  }

  @Test
  public void jsonWithCodeDeserializesCorrectly() throws Exception {
    final String json =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{"
            + "\"from\":\"0x0\", \"to\": \"0x0\"}, "
            + "\"latest\","
            + "{\""
            + ADDRESS_HEX1
            + "\":"
            + "{"
            + "\"balance\": \"0x01\","
            + "\"code\": \""
            + CODE_STRING
            + "\""
            + "}}],\"id\":1}";

    final JsonRpcRequestContext request = new JsonRpcRequestContext(readJsonAsJsonRpcRequest(json));
    final AccountOverrideMap accountOverrideParam =
        request.getRequiredParameter(2, AccountOverrideMap.class);

    final AccountOverride accountOverride =
        accountOverrideParam.get(Address.fromHexString(ADDRESS_HEX1));

    assertFalse(accountOverride.getNonce().isPresent());
    assertThat(accountOverride.getBalance()).isEqualTo(Optional.of(Wei.of(1)));
    assertThat(accountOverride.getCode()).isEqualTo(Optional.of(CODE_STRING));
    assertFalse(accountOverride.getStateDiff().isPresent());
  }

  @Test
  public void jsonWithStorageOverridesDeserializesCorrectly() throws Exception {
    final String json =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{"
            + "\"from\":\"0x0\", \"to\": \"0x0\"}, "
            + "\"latest\","
            + "{\""
            + ADDRESS_HEX1
            + "\":"
            + "{"
            + "\"balance\": \"0x01\","
            + "\"nonce\": 88,"
            + "\"stateDiff\": {"
            + "\""
            + STORAGE_KEY
            + "\": \""
            + STORAGE_VALUE
            + "\""
            + "}}}],\"id\":1}";

    final JsonRpcRequestContext request = new JsonRpcRequestContext(readJsonAsJsonRpcRequest(json));

    final AccountOverrideMap accountOverrideParam =
        request.getRequiredParameter(2, AccountOverrideMap.class);
    assertThat(accountOverrideParam.size()).isEqualTo(1);

    final AccountOverride accountOverride =
        accountOverrideParam.get(Address.fromHexString(ADDRESS_HEX1));
    assertThat(accountOverride.getNonce()).isEqualTo(Optional.of(88L));

    assertTrue(accountOverride.getStateDiff().isPresent());
    assertThat(accountOverride.getStateDiff().get().get(STORAGE_KEY)).isEqualTo(STORAGE_VALUE);
  }

  @Test
  public void jsonWithMultipleAccountOverridesDeserializesCorrectly() throws Exception {
    final String json =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{"
            + "\"from\":\"0x0\", \"to\": \"0x0\"}, "
            + "\"latest\","
            + "{\""
            + ADDRESS_HEX1
            + "\":"
            + "{"
            + "\"balance\": \"0x01\","
            + "\"nonce\": 88,"
            + "\"stateDiff\": {"
            + "\""
            + STORAGE_KEY
            + "\": \""
            + STORAGE_VALUE
            + "\""
            + "}},"
            + "\""
            + ADDRESS_HEX2
            + "\":"
            + "{"
            + "\"balance\": \"0xFF\","
            + "\"nonce\": 99,"
            + "\"stateDiff\": {"
            + "\""
            + STORAGE_KEY
            + "\": \""
            + STORAGE_VALUE
            + "\""
            + "}}}],\"id\":1}";

    final JsonRpcRequestContext request = new JsonRpcRequestContext(readJsonAsJsonRpcRequest(json));

    final AccountOverrideMap accountOverrideParam =
        request.getRequiredParameter(2, AccountOverrideMap.class);
    assertThat(accountOverrideParam.size()).isEqualTo(2);

    final AccountOverride accountOverride1 =
        accountOverrideParam.get(Address.fromHexString(ADDRESS_HEX1));
    assertThat(accountOverride1.getNonce()).isEqualTo(Optional.of(88L));
    assertThat(accountOverride1.getBalance()).isEqualTo(Optional.of(Wei.fromHexString("0x01")));
    assertTrue(accountOverride1.getStateDiff().isPresent());
    assertThat(accountOverride1.getStateDiff().get().get(STORAGE_KEY)).isEqualTo(STORAGE_VALUE);

    final AccountOverride accountOverride2 =
        accountOverrideParam.get(Address.fromHexString(ADDRESS_HEX2));
    assertThat(accountOverride2.getNonce()).isEqualTo(Optional.of(99L));
    assertThat(accountOverride2.getBalance()).isEqualTo(Optional.of(Wei.fromHexString("0xFF")));
    assertTrue(accountOverride2.getStateDiff().isPresent());
    assertThat(accountOverride2.getStateDiff().get().get(STORAGE_KEY)).isEqualTo(STORAGE_VALUE);
  }

  private JsonRpcRequest readJsonAsJsonRpcRequest(final String json) throws java.io.IOException {
    return new ObjectMapper().readValue(json, JsonRpcRequest.class);
  }
}
