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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.hyperledger.besu.ethereum.api.jsonrpc.BlockchainImporter;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcTestMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.testutil.BlockTestUtil;

import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class EthCallIntegrationTest {

  private static JsonRpcTestMethodsFactory BLOCKCHAIN;

  private JsonRpcMethod method;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    final String genesisJson =
        Resources.toString(BlockTestUtil.getTestGenesisUrl(), Charsets.UTF_8);

    BLOCKCHAIN =
        new JsonRpcTestMethodsFactory(
            new BlockchainImporter(BlockTestUtil.getTestBlockchainUrl(), genesisJson));
  }

  @Before
  public void setUp() {
    final Map<String, JsonRpcMethod> methods = BLOCKCHAIN.methods();
    method = methods.get("eth_call");
  }

  @Test
  public void shouldReturnExpectedResultForCallAtLatestBlock() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            Address.fromHexString("0x6295ee1b4f6dd65047762f924ecd367c17eabf8f"),
            null,
            null,
            null,
            null,
            null,
            Bytes.fromHexString("0x12a7b914"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            null, "0x0000000000000000000000000000000000000000000000000000000000000001");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnExpectedResultForCallAtSpecificBlock() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            Address.fromHexString("0x6295ee1b4f6dd65047762f924ecd367c17eabf8f"),
            null,
            null,
            null,
            null,
            null,
            Bytes.fromHexString("0x12a7b914"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "0x8");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            null, "0x0000000000000000000000000000000000000000000000000000000000000000");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnInvalidRequestWhenMissingToField() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            null,
            null,
            null,
            null,
            null,
            null,
            Bytes.fromHexString("0x12a7b914"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");

    final Throwable thrown = catchThrowable(() -> method.response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasNoCause()
        .hasMessage("Missing \"to\" field in call arguments");
  }

  @Test
  public void shouldReturnErrorWithGasLimitTooLow() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            Address.fromHexString("0x6295ee1b4f6dd65047762f924ecd367c17eabf8f"),
            Gas.ZERO,
            null,
            null,
            null,
            null,
            Bytes.fromHexString("0x12a7b914"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.INTRINSIC_GAS_EXCEEDS_LIMIT);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWithGasPriceTooHigh() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            Address.fromHexString("0x6295ee1b4f6dd65047762f924ecd367c17eabf8f"),
            null,
            Wei.fromHexString("0x10000000000000"),
            null,
            null,
            null,
            Bytes.fromHexString("0x12a7b914"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnEmptyHashResultForCallWithOnlyToField() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            null,
            Address.fromHexString("0x6295ee1b4f6dd65047762f924ecd367c17eabf8f"),
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, "0x");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_call", params));
  }
}
