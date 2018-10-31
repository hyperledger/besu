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
package tech.pegasys.pantheon.ethereum.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import tech.pegasys.pantheon.ethereum.jsonrpc.BlockchainImporter;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcTestMethodsFactory;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.CallParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.net.URL;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class EthCallIntegrationTest {

  private static final int CHAIN_ID = 123;
  private static JsonRpcTestMethodsFactory BLOCKCHAIN;

  private JsonRpcMethod method;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    final URL blocksUrl =
        EthGetBlockByNumberIntegrationTest.class
            .getClassLoader()
            .getResource("tech/pegasys/pantheon/ethereum/jsonrpc/jsonRpcTestBlockchain.blocks");

    final URL genesisJsonUrl =
        EthGetBlockByNumberIntegrationTest.class
            .getClassLoader()
            .getResource("tech/pegasys/pantheon/ethereum/jsonrpc/jsonRpcTestGenesis.json");

    assertThat(blocksUrl).isNotNull();
    assertThat(genesisJsonUrl).isNotNull();

    final String gensisjson = Resources.toString(genesisJsonUrl, Charsets.UTF_8);

    BLOCKCHAIN = new JsonRpcTestMethodsFactory(new BlockchainImporter(blocksUrl, gensisjson));
  }

  @Before
  public void setUp() {
    final Map<String, JsonRpcMethod> methods = BLOCKCHAIN.methods(CHAIN_ID);
    method = methods.get("eth_call");
  }

  @Test
  public void shouldReturnExpectedResultForCallAtLatestBlock() {
    final CallParameter callParameter =
        new CallParameter(
            "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b",
            "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f",
            null,
            null,
            null,
            "0x12a7b914");
    final JsonRpcRequest request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            null, "0x0000000000000000000000000000000000000000000000000000000000000001");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnExpectedResultForCallAtSpecificBlock() {
    final CallParameter callParameter =
        new CallParameter(
            "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b",
            "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f",
            null,
            null,
            null,
            "0x12a7b914");
    final JsonRpcRequest request = requestWithParams(callParameter, "0x8");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            null, "0x0000000000000000000000000000000000000000000000000000000000000000");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnInvalidRequestWhenMissingToField() {
    final CallParameter callParameter =
        new CallParameter(
            "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b", null, null, null, null, "0x12a7b914");
    final JsonRpcRequest request = requestWithParams(callParameter, "latest");

    final Throwable thrown = catchThrowable(() -> method.response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasNoCause()
        .hasMessage("Missing \"to\" field in call arguments");
  }

  @Test
  public void shouldReturnErrorWithGasLimitTooLow() {
    final CallParameter callParameter =
        new CallParameter(
            "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b",
            "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f",
            "0x0",
            null,
            null,
            "0x12a7b914");
    final JsonRpcRequest request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.INTRINSIC_GAS_EXCEEDS_LIMIT);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWithGasPriceTooHigh() {
    final CallParameter callParameter =
        new CallParameter(
            "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b",
            "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f",
            null,
            "0x10000000000000",
            null,
            "0x12a7b914");
    final JsonRpcRequest request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnEmptyHashResultForCallWithOnlyToField() {
    final CallParameter callParameter =
        new CallParameter(
            null, "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f", null, null, null, null);
    final JsonRpcRequest request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, "0x");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest("2.0", "eth_call", params);
  }
}
