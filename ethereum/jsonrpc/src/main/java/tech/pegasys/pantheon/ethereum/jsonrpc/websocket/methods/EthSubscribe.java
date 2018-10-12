package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.Quantity;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.InvalidSubscriptionRequestException;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscribeRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;

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
