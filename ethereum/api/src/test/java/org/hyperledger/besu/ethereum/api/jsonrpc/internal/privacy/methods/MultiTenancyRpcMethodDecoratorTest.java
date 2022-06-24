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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcUnauthorizedResponse;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.impl.UserImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MultiTenancyRpcMethodDecoratorTest {

  @Mock private JsonRpcMethod jsonRpcMethod;
  private final JsonRpcRequest rpcRequest = new JsonRpcRequest("1", "test", new String[] {"a"});

  @Test
  public void delegatesWhenHasValidToken() {
    final JsonObject principle = new JsonObject();
    principle.put("privacyPublicKey", "ABC123");
    final UserImpl user = new UserImpl(principle, new JsonObject());
    final JsonRpcRequestContext rpcRequestContext = new JsonRpcRequestContext(rpcRequest, user);

    when(jsonRpcMethod.response(rpcRequestContext))
        .thenReturn(new JsonRpcSuccessResponse("1", "b"));
    when(jsonRpcMethod.getName()).thenReturn("delegate");

    final MultiTenancyRpcMethodDecorator tokenRpcDecorator =
        new MultiTenancyRpcMethodDecorator(jsonRpcMethod);

    assertThat(tokenRpcDecorator.getName()).isEqualTo("delegate");

    final JsonRpcResponse response = tokenRpcDecorator.response(rpcRequestContext);
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo("b");
  }

  @Test
  public void failsWhenHasNoToken() {
    final JsonRpcRequestContext rpcRequestContext = new JsonRpcRequestContext(rpcRequest);
    final MultiTenancyRpcMethodDecorator tokenRpcDecorator =
        new MultiTenancyRpcMethodDecorator(jsonRpcMethod);
    when(jsonRpcMethod.getName()).thenReturn("delegate");

    assertThat(tokenRpcDecorator.getName()).isEqualTo("delegate");

    final JsonRpcResponse response = tokenRpcDecorator.response(rpcRequestContext);
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.UNAUTHORIZED);
    final JsonRpcUnauthorizedResponse errorResponse = (JsonRpcUnauthorizedResponse) response;
    assertThat(errorResponse.getError()).isEqualTo(JsonRpcError.UNAUTHORIZED);
  }

  @Test
  public void failsWhenTokenDoesNotHavePrivacyPublicKey() {
    final User user = new UserImpl(new JsonObject(), new JsonObject());
    final JsonRpcRequestContext rpcRequestContext = new JsonRpcRequestContext(rpcRequest, user);
    final MultiTenancyRpcMethodDecorator tokenRpcDecorator =
        new MultiTenancyRpcMethodDecorator(jsonRpcMethod);
    when(jsonRpcMethod.getName()).thenReturn("delegate");

    assertThat(tokenRpcDecorator.getName()).isEqualTo("delegate");

    final JsonRpcResponse response = tokenRpcDecorator.response(rpcRequestContext);
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError()).isEqualTo(JsonRpcError.INVALID_REQUEST);
  }
}
