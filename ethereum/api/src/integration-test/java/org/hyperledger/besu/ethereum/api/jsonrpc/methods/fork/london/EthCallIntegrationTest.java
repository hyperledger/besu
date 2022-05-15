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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods.fork.london;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.BlockchainImporter;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcTestMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.testutil.BlockTestUtil;

import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EthCallIntegrationTest {

  private static JsonRpcTestMethodsFactory BLOCKCHAIN;

  private JsonRpcMethod method;

  @BeforeAll
  public static void setUpOnce() throws Exception {
    final String genesisJson =
        Resources.toString(BlockTestUtil.getTestLondonGenesisUrl(), Charsets.UTF_8);

    BLOCKCHAIN =
        new JsonRpcTestMethodsFactory(
            new BlockchainImporter(BlockTestUtil.getTestLondonBlockchainUrl(), genesisJson));
  }

  @BeforeEach
  public void setUp() {
    final Map<String, JsonRpcMethod> methods = BLOCKCHAIN.methods();
    method = methods.get("eth_call");
  }

  @Test
  public void shouldReturnSuccessWithoutGasPriceAndEmptyBalance() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xdeadbeef00000000000000000000000000000000"),
            Address.fromHexString("0x9b8397f1b0fecd3a1a40cdd5e8221fa461898517"),
            null,
            null,
            null,
            null,
            null,
            Bytes.fromHexString("0x2e64cec1"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            null, "0x0000000000000000000000000000000000000000000000000000000000000001");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWithGasPriceTooHigh() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            Address.fromHexString("0x9b8397f1b0fecd3a1a40cdd5e8221fa461898517"),
            null,
            Wei.fromHexString("0x10000000000000"),
            null,
            null,
            null,
            Bytes.fromHexString("0x2e64cec1"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnSuccessWithValidGasPrice() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            Address.fromHexString("0x9b8397f1b0fecd3a1a40cdd5e8221fa461898517"),
            null,
            Wei.fromHexString("0x3B9ACA01"),
            null,
            null,
            null,
            Bytes.fromHexString("0x2e64cec1"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            null, "0x0000000000000000000000000000000000000000000000000000000000000001");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWithGasPriceLessThanCurrentBaseFee() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            Address.fromHexString("0x9b8397f1b0fecd3a1a40cdd5e8221fa461898517"),
            null,
            Wei.fromHexString("0x0A"),
            null,
            null,
            null,
            Bytes.fromHexString("0x2e64cec1"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnSuccessWithValidMaxFeePerGas() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            Address.fromHexString("0x9b8397f1b0fecd3a1a40cdd5e8221fa461898517"),
            null,
            null,
            null,
            Wei.fromHexString("0x3B9ACA01"),
            null,
            Bytes.fromHexString("0x2e64cec1"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            null, "0x0000000000000000000000000000000000000000000000000000000000000001");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnSuccessWithValidMaxFeePerGasAndMaxPriorityFeePerGas() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            Address.fromHexString("0x9b8397f1b0fecd3a1a40cdd5e8221fa461898517"),
            null,
            null,
            Wei.fromHexString("0x3B9ACA00"),
            Wei.fromHexString("0x3B9ACA01"),
            null,
            Bytes.fromHexString("0x2e64cec1"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            null, "0x0000000000000000000000000000000000000000000000000000000000000001");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWithValidMaxFeePerGasLessThanCurrentBaseFee() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            Address.fromHexString("0x9b8397f1b0fecd3a1a40cdd5e8221fa461898517"),
            null,
            null,
            null,
            Wei.fromHexString("0x0A"),
            null,
            Bytes.fromHexString("0x2e64cec1"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWithValidMaxFeePerGasLessThanMaxPriorityFeePerGas() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
            Address.fromHexString("0x9b8397f1b0fecd3a1a40cdd5e8221fa461898517"),
            null,
            null,
            Wei.fromHexString("0x3B9ACA02"),
            Wei.fromHexString("0x3B9ACA01"),
            null,
            Bytes.fromHexString("0x2e64cec1"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            null, JsonRpcError.MAX_PRIORITY_FEE_PER_GAS_EXCEEDS_MAX_FEE_PER_GAS);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWithMaxFeePerGasAndEmptyBalance() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(
            Address.fromHexString("0xdeadbeef00000000000000000000000000000000"),
            Address.fromHexString("0x9b8397f1b0fecd3a1a40cdd5e8221fa461898517"),
            null,
            null,
            null,
            Wei.fromHexString("0x3B9ACA01"),
            null,
            Bytes.fromHexString("0x2e64cec1"),
            null);
    final JsonRpcRequestContext request = requestWithParams(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_call", params));
  }
}
