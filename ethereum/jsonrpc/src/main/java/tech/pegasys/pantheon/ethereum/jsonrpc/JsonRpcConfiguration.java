package tech.pegasys.pantheon.ethereum.jsonrpc;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class JsonRpcConfiguration {

  public static final String DEFAULT_JSON_RPC_HOST = "127.0.0.1";
  public static final int DEFAULT_JSON_RPC_PORT = 8545;
  public static final Collection<RpcApis> DEFAULT_JSON_RPC_APIS =
      Arrays.asList(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3);

  private boolean enabled;
  private int port;
  private String host;
  private Collection<String> corsAllowedDomains = Collections.emptyList();
  private Collection<RpcApis> rpcApis;

  public enum RpcApis {
    DEBUG,
    ETH,
    MINER,
    NET,
    WEB3;

    public String getValue() {
      return this.name().toLowerCase();
    }
  }

  public static JsonRpcConfiguration createDefault() {
    final JsonRpcConfiguration config = new JsonRpcConfiguration();
    config.setEnabled(false);
    config.setPort(DEFAULT_JSON_RPC_PORT);
    config.setHost(DEFAULT_JSON_RPC_HOST);
    config.rpcApis = DEFAULT_JSON_RPC_APIS;
    return config;
  }

  private JsonRpcConfiguration() {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getPort() {
    return port;
  }

  public void setPort(final int port) {
    this.port = port;
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public Collection<String> getCorsAllowedDomains() {
    return corsAllowedDomains;
  }

  public void setCorsAllowedDomains(final Collection<String> corsAllowedDomains) {
    if (corsAllowedDomains != null) {
      this.corsAllowedDomains = corsAllowedDomains;
    }
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
        .add("corsAllowedDomains", corsAllowedDomains)
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
    final JsonRpcConfiguration that = (JsonRpcConfiguration) o;
    return enabled == that.enabled
        && port == that.port
        && Objects.equal(host, that.host)
        && Objects.equal(corsAllowedDomains, that.corsAllowedDomains)
        && Objects.equal(rpcApis, that.rpcApis);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(enabled, port, host, corsAllowedDomains, rpcApis);
  }
}
