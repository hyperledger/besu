/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcApis;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.common.base.MoreObjects;

public class WebSocketConfiguration {
  public static final String DEFAULT_WEBSOCKET_HOST = "127.0.0.1";
  public static final int DEFAULT_WEBSOCKET_PORT = 8546;
  public static final List<RpcApi> DEFAULT_WEBSOCKET_APIS =
      Arrays.asList(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3);

  private boolean enabled;
  private int port;
  private String host;
  private List<RpcApi> rpcApis;
  private boolean authenticationEnabled = false;
  private String authenticationCredentialsFile;
  private Collection<String> hostsWhitelist = Collections.singletonList("localhost");

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

  public Collection<RpcApi> getRpcApis() {
    return rpcApis;
  }

  public void setRpcApis(final List<RpcApi> rpcApis) {
    this.rpcApis = rpcApis;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("enabled", enabled)
        .add("port", port)
        .add("host", host)
        .add("rpcApis", rpcApis)
        .add("authenticationEnabled", authenticationEnabled)
        .add("authenticationCredentialsFile", authenticationCredentialsFile)
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
        && Objects.equals(host, that.host)
        && Objects.equals(rpcApis, that.rpcApis);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, port, host, rpcApis);
  }

  public boolean isAuthenticationEnabled() {
    return authenticationEnabled;
  }

  public void setAuthenticationEnabled(final boolean authenticationEnabled) {
    this.authenticationEnabled = authenticationEnabled;
  }

  public void setAuthenticationCredentialsFile(final String authenticationCredentialsFile) {
    this.authenticationCredentialsFile = authenticationCredentialsFile;
  }

  public String getAuthenticationCredentialsFile() {
    return authenticationCredentialsFile;
  }

  public void setHostsWhitelist(final Collection<String> hostsWhitelist) {
    this.hostsWhitelist = hostsWhitelist;
  }

  public Collection<String> getHostsWhitelist() {
    return Collections.unmodifiableCollection(this.hostsWhitelist);
  }
}
