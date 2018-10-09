package net.consensys.pantheon.ethereum.jsonrpc.websocket;

import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration.RpcApis;

import java.util.Arrays;
import java.util.Collection;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class WebSocketConfiguration {

  public static final String DEFAULT_WEBSOCKET_HOST = "127.0.0.1";
  public static final int DEFAULT_WEBSOCKET_PORT = 8546;
  public static final Collection<RpcApis> DEFAULT_WEBSOCKET_APIS =
      Arrays.asList(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3);

  private boolean enabled;
  private int port;
  private String host;
  private Collection<RpcApis> rpcApis;

  public static WebSocketConfiguration createDefault() {
    final WebSocketConfiguration config = new WebSocketConfiguration();
    config.setEnabled(false);
    config.setHost(DEFAULT_WEBSOCKET_HOST);
    config.setPort(DEFAULT_WEBSOCKET_PORT);
    config.setRpcApis(DEFAULT_WEBSOCKET_APIS);
    return config;
  }

  private WebSocketConfiguration() {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public String getHost() {
    return host;
  }

  public void setPort(final int port) {
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  public Collection<RpcApis> getRpcApis() {
    return rpcApis;
  }

  public void setRpcApis(final Collection<RpcApis> rpcApis) {
    this.rpcApis = rpcApis;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("enabled", enabled)
        .add("port", port)
        .add("host", host)
        .add("rpcApis", rpcApis)
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WebSocketConfiguration that = (WebSocketConfiguration) o;
    return enabled == that.enabled
        && port == that.port
        && Objects.equal(host, that.host)
        && Objects.equal(rpcApis, that.rpcApis);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(enabled, port, host, rpcApis);
  }
}
