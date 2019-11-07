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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.UnsignedLongParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketRpcRequest;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;

import java.util.Optional;

public class SubscriptionRequestMapper {

  private final JsonRpcParameter parameter;

  public SubscriptionRequestMapper(final JsonRpcParameter parameter) {
    this.parameter = parameter;
  }

  public SubscribeRequest mapSubscribeRequest(final JsonRpcRequest jsonRpcRequest)
      throws InvalidSubscriptionRequestException {
    try {
      final WebSocketRpcRequest webSocketRpcRequest = validateRequest(jsonRpcRequest);

      final SubscriptionType subscriptionType =
          parameter.required(webSocketRpcRequest.getParams(), 0, SubscriptionType.class);
      switch (subscriptionType) {
        case NEW_BLOCK_HEADERS:
          {
            final boolean includeTransactions = includeTransactions(webSocketRpcRequest);
            return parseNewBlockHeadersRequest(webSocketRpcRequest, includeTransactions);
          }
        case LOGS:
          {
            return parseLogsRequest(webSocketRpcRequest, parameter);
          }
        case NEW_PENDING_TRANSACTIONS:
        case SYNCING:
        default:
          final boolean includeTransactions = includeTransactions(webSocketRpcRequest);
          return new SubscribeRequest(
              subscriptionType, null, includeTransactions, webSocketRpcRequest.getConnectionId());
      }
    } catch (final Exception e) {
      throw new InvalidSubscriptionRequestException("Error parsing subscribe request", e);
    }
  }

  private boolean includeTransactions(final WebSocketRpcRequest webSocketRpcRequest) {
    final Optional<SubscriptionParam> params =
        parameter.optional(webSocketRpcRequest.getParams(), 1, SubscriptionParam.class);
    return params.isPresent() && params.get().includeTransaction();
  }

  private SubscribeRequest parseNewBlockHeadersRequest(
      final WebSocketRpcRequest request, final Boolean includeTransactions) {
    return new SubscribeRequest(
        SubscriptionType.NEW_BLOCK_HEADERS, null, includeTransactions, request.getConnectionId());
  }

  private SubscribeRequest parseLogsRequest(
      final WebSocketRpcRequest request, final JsonRpcParameter parameter) {
    final LogsQuery logsQuery = parameter.required(request.getParams(), 1, LogsQuery.class);
    return new SubscribeRequest(SubscriptionType.LOGS, logsQuery, null, request.getConnectionId());
  }

  public UnsubscribeRequest mapUnsubscribeRequest(final JsonRpcRequest jsonRpcRequest)
      throws InvalidSubscriptionRequestException {
    try {
      final WebSocketRpcRequest webSocketRpcRequest = validateRequest(jsonRpcRequest);

      final long subscriptionId =
          parameter
              .required(webSocketRpcRequest.getParams(), 0, UnsignedLongParameter.class)
              .getValue();
      return new UnsubscribeRequest(subscriptionId, webSocketRpcRequest.getConnectionId());
    } catch (final Exception e) {
      throw new InvalidSubscriptionRequestException("Error parsing subscribe request", e);
    }
  }

  private WebSocketRpcRequest validateRequest(final JsonRpcRequest jsonRpcRequest) {
    if (jsonRpcRequest instanceof WebSocketRpcRequest) {
      return (WebSocketRpcRequest) jsonRpcRequest;
    } else {
      throw new InvalidRequestException("Invalid request received.");
    }
  }
}
