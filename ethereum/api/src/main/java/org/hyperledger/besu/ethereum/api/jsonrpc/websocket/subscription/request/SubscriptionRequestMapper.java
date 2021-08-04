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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.UnsignedLongParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketRpcRequest;

import java.util.Optional;

public class SubscriptionRequestMapper {

  public SubscriptionRequestMapper() {}

  public SubscribeRequest mapSubscribeRequest(final JsonRpcRequestContext jsonRpcRequestContext)
      throws InvalidSubscriptionRequestException {
    try {
      final WebSocketRpcRequest webSocketRpcRequestBody = validateRequest(jsonRpcRequestContext);

      final SubscriptionType subscriptionType =
          webSocketRpcRequestBody.getRequiredParameter(0, SubscriptionType.class);
      switch (subscriptionType) {
        case NEW_BLOCK_HEADERS:
          {
            final boolean includeTransactions = includeTransactions(webSocketRpcRequestBody);
            return parseNewBlockHeadersRequest(webSocketRpcRequestBody, includeTransactions);
          }
        case LOGS:
          {
            return parseLogsRequest(webSocketRpcRequestBody);
          }
        case NEW_PENDING_TRANSACTIONS:
        case SYNCING:
        default:
          final boolean includeTransactions = includeTransactions(webSocketRpcRequestBody);
          return new SubscribeRequest(
              subscriptionType,
              null,
              includeTransactions,
              webSocketRpcRequestBody.getConnectionId());
      }
    } catch (final Exception e) {
      throw new InvalidSubscriptionRequestException("Error parsing subscribe request", e);
    }
  }

  private boolean includeTransactions(final WebSocketRpcRequest webSocketRpcRequestBody) {
    final Optional<SubscriptionParam> params =
        webSocketRpcRequestBody.getOptionalParameter(1, SubscriptionParam.class);
    return params.isPresent() && params.get().includeTransaction();
  }

  private SubscribeRequest parseNewBlockHeadersRequest(
      final WebSocketRpcRequest request, final Boolean includeTransactions) {
    return new SubscribeRequest(
        SubscriptionType.NEW_BLOCK_HEADERS, null, includeTransactions, request.getConnectionId());
  }

  private SubscribeRequest parseLogsRequest(final WebSocketRpcRequest request) {
    final FilterParameter filterParameter = request.getRequiredParameter(1, FilterParameter.class);
    return new SubscribeRequest(
        SubscriptionType.LOGS, filterParameter, null, request.getConnectionId());
  }

  public UnsubscribeRequest mapUnsubscribeRequest(final JsonRpcRequestContext jsonRpcRequestContext)
      throws InvalidSubscriptionRequestException {
    try {
      final WebSocketRpcRequest webSocketRpcRequestBody = validateRequest(jsonRpcRequestContext);

      final long subscriptionId =
          webSocketRpcRequestBody.getRequiredParameter(0, UnsignedLongParameter.class).getValue();
      return new UnsubscribeRequest(subscriptionId, webSocketRpcRequestBody.getConnectionId());
    } catch (final Exception e) {
      throw new InvalidSubscriptionRequestException("Error parsing unsubscribe request", e);
    }
  }

  public PrivateSubscribeRequest mapPrivateSubscribeRequest(
      final JsonRpcRequestContext jsonRpcRequestContext, final String privacyUserId)
      throws InvalidSubscriptionRequestException {
    try {
      final WebSocketRpcRequest webSocketRpcRequestBody = validateRequest(jsonRpcRequestContext);

      final String privacyGroupId = webSocketRpcRequestBody.getRequiredParameter(0, String.class);
      final SubscriptionType subscriptionType =
          webSocketRpcRequestBody.getRequiredParameter(1, SubscriptionType.class);

      switch (subscriptionType) {
        case LOGS:
          {
            final FilterParameter filterParameter =
                jsonRpcRequestContext.getRequiredParameter(2, FilterParameter.class);
            return new PrivateSubscribeRequest(
                SubscriptionType.LOGS,
                filterParameter,
                null,
                webSocketRpcRequestBody.getConnectionId(),
                privacyGroupId,
                privacyUserId);
          }
        default:
          throw new InvalidSubscriptionRequestException(
              "Invalid subscribe request. Invalid private subscription type.");
      }
    } catch (final InvalidSubscriptionRequestException e) {
      throw e;
    } catch (final Exception e) {
      throw new InvalidSubscriptionRequestException("Error parsing subscribe request", e);
    }
  }

  public PrivateUnsubscribeRequest mapPrivateUnsubscribeRequest(
      final JsonRpcRequestContext jsonRpcRequestContext)
      throws InvalidSubscriptionRequestException {
    try {
      final WebSocketRpcRequest webSocketRpcRequestBody = validateRequest(jsonRpcRequestContext);

      final String privacyGroupId = webSocketRpcRequestBody.getRequiredParameter(0, String.class);
      final long subscriptionId =
          webSocketRpcRequestBody.getRequiredParameter(1, UnsignedLongParameter.class).getValue();
      return new PrivateUnsubscribeRequest(
          subscriptionId, webSocketRpcRequestBody.getConnectionId(), privacyGroupId);
    } catch (final Exception e) {
      throw new InvalidSubscriptionRequestException("Error parsing unsubscribe request", e);
    }
  }

  private WebSocketRpcRequest validateRequest(final JsonRpcRequestContext jsonRpcRequestContext) {
    if (jsonRpcRequestContext.getRequest() instanceof WebSocketRpcRequest) {
      return (WebSocketRpcRequest) jsonRpcRequestContext.getRequest();
    } else {
      throw new InvalidRequestException("Invalid request received.");
    }
  }
}
