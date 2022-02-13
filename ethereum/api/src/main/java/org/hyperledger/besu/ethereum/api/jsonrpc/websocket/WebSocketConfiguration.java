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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket;

import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.DEFAULT_RPC_APIS;

import org.hyperledger.besu.ethereum.api.handlers.TimeoutOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.JwtAlgorithm;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.common.base.MoreObjects;

public class WebSocketConfiguration {
  public static final String DEFAULT_WEBSOCKET_HOST = "127.0.0.1";
  public static final int DEFAULT_WEBSOCKET_PORT = 8546;
  public static final int DEFAULT_WEBSOCKET_ENGINE_PORT = 8551;
  public static final int DEFAULT_WEBSOCKET_MAX_FRAME_SIZE = 1024 * 1024;
  public static final int DEFAULT_MAX_ACTIVE_CONNECTIONS = 80;

  private boolean enabled;
  private int port;
  private String host;
  private List<String> rpcApis;
  private List<String> rpcApisNoAuth = Collections.emptyList();
  private boolean authenticationEnabled = false;
  private String authenticationCredentialsFile;
  private List<String> hostsAllowlist = Arrays.asList("localhost", "127.0.0.1");
  private File authenticationPublicKeyFile;
  private JwtAlgorithm authenticationAlgorithm = JwtAlgorithm.RS256;
  private long timeoutSec;
  private int maxActiveConnections;
  private int maxFrameSize;

  public static WebSocketConfiguration createDefault() {
    final WebSocketConfiguration config = new WebSocketConfiguration();
    config.setEnabled(false);
    config.setHost(DEFAULT_WEBSOCKET_HOST);
    config.setPort(DEFAULT_WEBSOCKET_PORT);
    config.setRpcApis(DEFAULT_RPC_APIS);
    config.setTimeoutSec(TimeoutOptions.defaultOptions().getTimeoutSeconds());
    config.setMaxActiveConnections(DEFAULT_MAX_ACTIVE_CONNECTIONS);
    config.setMaxFrameSize(DEFAULT_WEBSOCKET_MAX_FRAME_SIZE);
    return config;
  }

  public static WebSocketConfiguration createEngineDefault() {
    final WebSocketConfiguration config = createDefault();
    config.setPort(DEFAULT_WEBSOCKET_ENGINE_PORT);
    config.setRpcApis(Arrays.asList("ENGINE", "ETH"));
    config.setHostsAllowlist(Arrays.asList("localhost", "127.0.0.1"));
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

  public Collection<String> getRpcApis() {
    return rpcApis;
  }

  public void setRpcApis(final List<String> rpcApis) {
    this.rpcApis = rpcApis;
  }

  public Collection<String> getRpcApisNoAuth() {
    return rpcApisNoAuth;
  }

  public void setRpcApisNoAuth(final List<String> rpcApis) {
    this.rpcApisNoAuth = rpcApis;
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

  public void setHostsAllowlist(final List<String> hostsAllowlist) {
    this.hostsAllowlist = hostsAllowlist;
  }

  public Collection<String> getHostsAllowlist() {
    return Collections.unmodifiableCollection(this.hostsAllowlist);
  }

  public File getAuthenticationPublicKeyFile() {
    return authenticationPublicKeyFile;
  }

  public void setAuthenticationPublicKeyFile(final File authenticationPublicKeyFile) {
    this.authenticationPublicKeyFile = authenticationPublicKeyFile;
  }

  public JwtAlgorithm getAuthenticationAlgorithm() {
    return authenticationAlgorithm;
  }

  public void setAuthenticationAlgorithm(final JwtAlgorithm algorithm) {
    authenticationAlgorithm = algorithm;
  }

  public long getTimeoutSec() {
    return timeoutSec;
  }

  public void setTimeoutSec(final long timeoutSec) {
    this.timeoutSec = timeoutSec;
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
        && authenticationEnabled == that.authenticationEnabled
        && Objects.equals(host, that.host)
        && Objects.equals(rpcApis, that.rpcApis)
        && Objects.equals(authenticationCredentialsFile, that.authenticationCredentialsFile)
        && Objects.equals(hostsAllowlist, that.hostsAllowlist)
        && Objects.equals(authenticationPublicKeyFile, that.authenticationPublicKeyFile)
        && timeoutSec == that.timeoutSec;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        enabled,
        port,
        host,
        rpcApis,
        authenticationEnabled,
        authenticationCredentialsFile,
        hostsAllowlist,
        authenticationPublicKeyFile,
        timeoutSec);
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
        .add("hostsAllowlist", hostsAllowlist)
        .add("authenticationPublicKeyFile", authenticationPublicKeyFile)
        .add("timeoutSec", timeoutSec)
        .toString();
  }

  public int getMaxActiveConnections() {
    return maxActiveConnections;
  }

  public void setMaxActiveConnections(final int maxActiveConnections) {
    this.maxActiveConnections = maxActiveConnections;
  }

  public void setMaxFrameSize(final int maxFrameSize) {
    this.maxFrameSize = maxFrameSize;
  }

  public Integer getMaxFrameSize() {
    return maxFrameSize;
  }
}
