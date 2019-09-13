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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.methods;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.SubscriptionNotFoundException;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.request.InvalidSubscriptionRequestException;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.request.UnsubscribeRequest;

public class EthUnsubscribe extends AbstractSubscriptionMethod {

  EthUnsubscribe(
      final SubscriptionManager subscriptionManager, final SubscriptionRequestMapper mapper) {
    super(subscriptionManager, mapper);
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_UNSUBSCRIBE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    try {
      final UnsubscribeRequest unsubscribeRequest = getMapper().mapUnsubscribeRequest(request);
      final boolean unsubscribed = subscriptionManager().unsubscribe(unsubscribeRequest);

      return new JsonRpcSuccessResponse(request.getId(), unsubscribed);
    } catch (final InvalidSubscriptionRequestException isEx) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_REQUEST);
    } catch (final SubscriptionNotFoundException snfEx) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.SUBSCRIPTION_NOT_FOUND);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INTERNAL_ERROR);
    }
  }
}
