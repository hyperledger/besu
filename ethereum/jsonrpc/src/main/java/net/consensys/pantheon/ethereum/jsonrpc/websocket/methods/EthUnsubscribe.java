package net.consensys.pantheon.ethereum.jsonrpc.websocket.methods;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionNotFoundException;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.InvalidSubscriptionRequestException;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.UnsubscribeRequest;

public class EthUnsubscribe extends AbstractSubscriptionMethod {

  EthUnsubscribe(
      final SubscriptionManager subscriptionManager, final SubscriptionRequestMapper mapper) {
    super(subscriptionManager, mapper);
  }

  @Override
  public String getName() {
    return "eth_unsubscribe";
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
