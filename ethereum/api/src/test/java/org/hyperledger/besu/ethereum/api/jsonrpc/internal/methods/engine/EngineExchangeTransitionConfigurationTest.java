/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineExchangeTransitionConfigurationParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;

import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EngineExchangeTransitionConfigurationTest {
  private EngineExchangeTransitionConfiguration method;
  private static final Vertx vertx = Vertx.vertx();

  @Mock private ProtocolContext protocolContext;
  @Mock private MergeContext mergeContext;

  @Before
  public void setUp() {
    when(protocolContext.getConsensusContext(Mockito.any())).thenReturn(mergeContext);

    this.method = new EngineExchangeTransitionConfiguration(vertx, protocolContext);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    // will break as specs change, intentional:
    assertThat(method.getName()).isEqualTo("engine_exchangeTransitionConfigurationV1");
  }

  @Test
  public void shouldReturnInvalidParamsOnTerminalBlockNumberNotZero() {
    var response =
        resp(new EngineExchangeTransitionConfigurationParameter("0", Hash.ZERO.toHexString(), 1));

    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    JsonRpcErrorResponse res = ((JsonRpcErrorResponse) response);
    assertThat(res.getError()).isEqualTo(JsonRpcError.INVALID_PARAMS);
  }

  private JsonRpcResponse resp(final EngineExchangeTransitionConfigurationParameter param) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                RpcMethod.ENGINE_EXCHANGE_TRANSITION_CONFIGURATION.getMethodName(),
                new Object[] {param})));
  }

  //    private EngineExchangeTransitionConfigurationResult fromSuccessResp(final JsonRpcResponse
  // resp) {
  //        assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
  //        return Optional.of(resp)
  //                .map(JsonRpcSuccessResponse.class::cast)
  //                .map(JsonRpcSuccessResponse::getResult)
  //                .map(EngineExchangeTransitionConfigurationResult.class::cast)
  //                .get();
  //    }
}
