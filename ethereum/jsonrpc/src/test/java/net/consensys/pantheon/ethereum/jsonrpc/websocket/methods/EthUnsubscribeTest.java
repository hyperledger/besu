package net.consensys.pantheon.ethereum.jsonrpc.websocket.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionNotFoundException;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.InvalidSubscriptionRequestException;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.UnsubscribeRequest;

import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Test;

public class EthUnsubscribeTest {

  private EthUnsubscribe ethUnsubscribe;
  private SubscriptionManager subscriptionManagerMock;
  private SubscriptionRequestMapper mapperMock;
  private final String CONNECTION_ID = "test-connection-id";

  @Before
  public void before() {
    subscriptionManagerMock = mock(SubscriptionManager.class);
    mapperMock = mock(SubscriptionRequestMapper.class);
    ethUnsubscribe = new EthUnsubscribe(subscriptionManagerMock, mapperMock);
  }

  @Test
  public void nameIsEthUnsubscribe() {
    assertThat(ethUnsubscribe.getName()).isEqualTo("eth_unsubscribe");
  }

  @Test
  public void responseContainsUnsubscribeStatus() {
    final JsonRpcRequest request = createJsonRpcRequest();
    final UnsubscribeRequest unsubscribeRequest = new UnsubscribeRequest(1L, CONNECTION_ID);
    when(mapperMock.mapUnsubscribeRequest(eq(request))).thenReturn(unsubscribeRequest);
    when(subscriptionManagerMock.unsubscribe(eq(unsubscribeRequest))).thenReturn(true);

    final JsonRpcSuccessResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getId(), true);

    assertThat(ethUnsubscribe.response(request)).isEqualTo(expectedResponse);
  }

  @Test
  public void invalidUnsubscribeRequestReturnsInvalidRequestResponse() {
    final JsonRpcRequest request = createJsonRpcRequest();
    when(mapperMock.mapUnsubscribeRequest(any()))
        .thenThrow(new InvalidSubscriptionRequestException());

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_REQUEST);

    assertThat(ethUnsubscribe.response(request)).isEqualTo(expectedResponse);
  }

  @Test
  public void whenSubscriptionNotFoundReturnError() {
    final JsonRpcRequest request = createJsonRpcRequest();
    when(mapperMock.mapUnsubscribeRequest(any())).thenReturn(mock(UnsubscribeRequest.class));
    when(subscriptionManagerMock.unsubscribe(any()))
        .thenThrow(new SubscriptionNotFoundException(1L));

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.SUBSCRIPTION_NOT_FOUND);

    assertThat(ethUnsubscribe.response(request)).isEqualTo(expectedResponse);
  }

  @Test
  public void uncaughtErrorOnSubscriptionManagerReturnsInternalErrorResponse() {
    final JsonRpcRequest request = createJsonRpcRequest();
    when(mapperMock.mapUnsubscribeRequest(any())).thenReturn(mock(UnsubscribeRequest.class));
    when(subscriptionManagerMock.unsubscribe(any())).thenThrow(new RuntimeException());

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INTERNAL_ERROR);

    assertThat(ethUnsubscribe.response(request)).isEqualTo(expectedResponse);
  }

  private JsonRpcRequest createJsonRpcRequest() {
    return Json.decodeValue(
        "{\"id\": 1, \"method\": \"eth_unsubscribe\", \"params\": [\"0x0\"]}",
        JsonRpcRequest.class);
  }
}
