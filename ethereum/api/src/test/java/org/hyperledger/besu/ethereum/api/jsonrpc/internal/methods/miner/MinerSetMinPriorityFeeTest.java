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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MinerSetMinPriorityFeeTest {
  MiningConfiguration miningConfiguration = MiningConfiguration.newDefault();
  private MinerSetMinPriorityFee method;

  @BeforeEach
  public void setUp() {
    method = new MinerSetMinPriorityFee(miningConfiguration);
  }

  @Test
  public void shouldReturnInvalidParamsWhenParameterIsInvalid() {
    final String invalidMinPriorityFee =
        "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
    final var request = request(invalidMinPriorityFee);
    method.response(request);

    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(
            request.getRequest().getId(),
            new JsonRpcError(
                RpcErrorType.INVALID_MIN_PRIORITY_FEE_PARAMS,
                "Hex value is too large: expected at most 32 bytes but got 33"));

    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  public void shouldReturnInvalidParamsWhenParameterIsMissing() {
    final var request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", method.getName(), new Object[] {}));
    method.response(request);
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(
            request.getRequest().getId(),
            new JsonRpcError(
                RpcErrorType.INVALID_MIN_PRIORITY_FEE_PARAMS,
                "Missing required json rpc parameter at index 0"));
    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  public void shouldReturnTrueWhenChangeMinPriorityFee() {
    final String newMinPriorityFee = "0x10";
    final var request = request(newMinPriorityFee);
    method.response(request);

    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getRequest().getId(), true);
    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    assertThat(miningConfiguration.getMinPriorityFeePerGas())
        .isEqualTo(Wei.fromHexString(newMinPriorityFee));
  }

  private JsonRpcRequestContext request(final String newMinPriorityFee) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", method.getName(), new Object[] {newMinPriorityFee}));
  }
}
