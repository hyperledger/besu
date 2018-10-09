package net.consensys.pantheon.ethereum.jsonrpc.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration.RpcApis;

import org.junit.Test;

public class WebSocketConfigurationTest {

  @Test
  public void defaultConfiguration() {
    final WebSocketConfiguration configuration = WebSocketConfiguration.createDefault();

    assertThat(configuration.isEnabled()).isFalse();
    assertThat(configuration.getHost()).isEqualTo("127.0.0.1");
    assertThat(configuration.getPort()).isEqualTo(8546);
    assertThat(configuration.getRpcApis())
        .containsExactlyInAnyOrder(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3);
  }
}
