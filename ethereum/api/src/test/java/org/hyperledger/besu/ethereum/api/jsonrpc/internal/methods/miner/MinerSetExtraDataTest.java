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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;

import java.nio.charset.StandardCharsets;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MinerSetExtraDataTest {
  MiningConfiguration miningConfiguration = MiningConfiguration.newDefault();
  private MinerSetExtraData method;

  @BeforeEach
  public void setUp() {
    method = new MinerSetExtraData(miningConfiguration);
  }

  @Test
  public void shouldChangeExtraData() {
    final String newExtraData = " pippo 🐐 | ";
    final var request =
        request(Bytes.wrap(newExtraData.getBytes(StandardCharsets.UTF_8)).toHexString());
    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getRequest().getId(), true);

    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    final var currExtraData = miningConfiguration.getExtraData();
    assertThat(new String(currExtraData.toArray(), StandardCharsets.UTF_8)).isEqualTo(newExtraData);
  }

  private JsonRpcRequestContext request(final String newExtraData) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", method.getName(), new Object[] {newExtraData}));
  }

  @Test
  public void shouldNotTrimLeadingZeros() {
    final var zeroPrefixedExtraData = "0010203";
    final var request = request(zeroPrefixedExtraData);

    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getRequest().getId(), true);

    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    final var currExtraData = miningConfiguration.getExtraData();
    assertThat(new String(currExtraData.toArray(), StandardCharsets.UTF_8))
        .isEqualTo(
            new String(
                Bytes.fromHexStringLenient(zeroPrefixedExtraData).toArray(),
                StandardCharsets.UTF_8));
  }

  @Test
  public void shouldReturnErrorWhenExtraDataNotHex() {
    final var request = request("not hex string");

    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(
            request.getRequest().getId(),
            new JsonRpcError(
                RpcErrorType.INVALID_EXTRA_DATA_PARAMS,
                "Illegal character 'n' found at index 0 in hex binary representation"));

    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  public void shouldReturnErrorWhenExtraDataTooLong() {
    final var tooLongExtraData = "shouldReturnFalseWhenExtraDataTooLong";
    final var request =
        request(Bytes.wrap(tooLongExtraData.getBytes(StandardCharsets.UTF_8)).toHexString());

    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(
            request.getRequest().getId(),
            new JsonRpcError(
                RpcErrorType.INVALID_EXTRA_DATA_PARAMS,
                "Hex value is too large: expected at most 32 bytes but got 37"));

    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
}
