package net.consensys.pantheon.ethereum.jsonrpc.websocket.methods;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.Quantity;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.InvalidSubscriptionRequestException;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscribeRequest;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;

public class EthSubscribe extends AbstractSubscriptionMethod {

  EthSubscribe(
      final SubscriptionManager subscriptionManager, final SubscriptionRequestMapper mapper) {
    super(subscriptionManager, mapper);
  }

  @Override
  public String getName() {
    return "eth_subscribe";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    try {
      final SubscribeRequest subscribeRequest = getMapper().mapSubscribeRequest(request);
      final Long subscriptionId = subscriptionManager().subscribe(subscribeRequest);

      return new JsonRpcSuccessResponse(request.getId(), Quantity.create(subscriptionId));
    } catch (final InvalidSubscriptionRequestException isEx) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_REQUEST);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INTERNAL_ERROR);
    }
  }
}
