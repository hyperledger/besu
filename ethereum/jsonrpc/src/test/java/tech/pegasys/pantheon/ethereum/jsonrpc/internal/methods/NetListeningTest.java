package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NetListeningTest {

  private NetListening method;

  @Mock private P2PNetwork p2PNetwork;

  @Before
  public void before() {
    this.method = new NetListening(p2PNetwork);
  }

  @Test
  public void shouldReturnTrueWhenNetworkIsListening() {
    when(p2PNetwork.isListening()).thenReturn(true);

    final JsonRpcRequest request = netListeningRequest();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, true);

    assertThat(method.response(request)).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnFalseWhenNetworkIsNotListening() {
    when(p2PNetwork.isListening()).thenReturn(false);

    final JsonRpcRequest request = netListeningRequest();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, false);

    assertThat(method.response(request)).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest netListeningRequest() {
    return new JsonRpcRequest("2.0", "net_listening", new Object[] {});
  }
}
