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

public class MinerSetMinGasPriceTest {
  MiningConfiguration miningConfiguration = MiningConfiguration.newDefault();
  private MinerSetMinGasPrice method;

  @BeforeEach
  public void setUp() {
    method = new MinerSetMinGasPrice(miningConfiguration);
  }

  @Test
  public void shouldReturnFalseWhenParameterIsInvalid() {
    final String newMinGasPrice = "-1";
    final var request = request(newMinGasPrice);

    method.response(request);
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(
            request.getRequest().getId(),
            new JsonRpcError(
                RpcErrorType.INVALID_MIN_GAS_PRICE_PARAMS,
                "Illegal character '-' found at index 0 in hex binary representation"));

    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  public void shouldChangeMinPriorityFee() {
    final String newMinGasPrice = "0x10";
    final var request = request(newMinGasPrice);
    method.response(request);
    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getRequest().getId(), true);

    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    assertThat(miningConfiguration.getMinTransactionGasPrice())
        .isEqualTo(Wei.fromHexString(newMinGasPrice));
  }

  private JsonRpcRequestContext request(final String newMinGasPrice) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", method.getName(), new Object[] {newMinGasPrice}));
  }
}
