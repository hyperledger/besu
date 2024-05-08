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

/** The type Web socket configuration. */
public class WebSocketConfiguration {
  /** The constant DEFAULT_WEBSOCKET_HOST. */
  public static final String DEFAULT_WEBSOCKET_HOST = "127.0.0.1";

  /** The constant DEFAULT_WEBSOCKET_PORT. */
  public static final int DEFAULT_WEBSOCKET_PORT = 8546;

  /** The constant DEFAULT_WEBSOCKET_ENGINE_PORT. */
  public static final int DEFAULT_WEBSOCKET_ENGINE_PORT = 8551;

  /** The constant DEFAULT_WEBSOCKET_MAX_FRAME_SIZE. */
  public static final int DEFAULT_WEBSOCKET_MAX_FRAME_SIZE = 1024 * 1024;

  /** The constant DEFAULT_MAX_ACTIVE_CONNECTIONS. */
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

  /**
   * Create default web socket configuration.
   *
   * @return the web socket configuration
   */
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

  /**
   * Create engine default web socket configuration.
   *
   * @return the web socket configuration
   */
  public static WebSocketConfiguration createEngineDefault() {
    final WebSocketConfiguration config = createDefault();
    config.setPort(DEFAULT_WEBSOCKET_ENGINE_PORT);
    config.setRpcApis(Arrays.asList("ENGINE", "ETH"));
    config.setHostsAllowlist(Arrays.asList("localhost", "127.0.0.1"));
    return config;
  }

  private WebSocketConfiguration() {}

  /**
   * Is enabled boolean.
   *
   * @return the boolean
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets enabled.
   *
   * @param enabled the enabled
   */
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Sets host.
   *
   * @param host the host
   */
  public void setHost(final String host) {
    this.host = host;
  }

  /**
   * Gets host.
   *
   * @return the host
   */
  public String getHost() {
    return host;
  }

  /**
   * Sets port.
   *
   * @param port the port
   */
  public void setPort(final int port) {
    this.port = port;
  }

  /**
   * Gets port.
   *
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Gets rpc apis.
   *
   * @return the rpc apis
   */
  public Collection<String> getRpcApis() {
    return rpcApis;
  }

  /**
   * Sets rpc apis.
   *
   * @param rpcApis the rpc apis
   */
  public void setRpcApis(final List<String> rpcApis) {
    this.rpcApis = rpcApis;
  }

  /**
   * Gets rpc apis no auth.
   *
   * @return the rpc apis no auth
   */
  public Collection<String> getRpcApisNoAuth() {
    return rpcApisNoAuth;
  }

  /**
   * Sets rpc apis no auth.
   *
   * @param rpcApis the rpc apis
   */
  public void setRpcApisNoAuth(final List<String> rpcApis) {
    this.rpcApisNoAuth = rpcApis;
  }

  /**
   * Is authentication enabled boolean.
   *
   * @return the boolean
   */
  public boolean isAuthenticationEnabled() {
    return authenticationEnabled;
  }

  /**
   * Sets authentication enabled.
   *
   * @param authenticationEnabled the authentication enabled
   */
  public void setAuthenticationEnabled(final boolean authenticationEnabled) {
    this.authenticationEnabled = authenticationEnabled;
  }

  /**
   * Sets authentication credentials file.
   *
   * @param authenticationCredentialsFile the authentication credentials file
   */
  public void setAuthenticationCredentialsFile(final String authenticationCredentialsFile) {
    this.authenticationCredentialsFile = authenticationCredentialsFile;
  }

  /**
   * Gets authentication credentials file.
   *
   * @return the authentication credentials file
   */
  public String getAuthenticationCredentialsFile() {
    return authenticationCredentialsFile;
  }

  /**
   * Sets hosts allowlist.
   *
   * @param hostsAllowlist the hosts allowlist
   */
  public void setHostsAllowlist(final List<String> hostsAllowlist) {
    this.hostsAllowlist = hostsAllowlist;
  }

  /**
   * Gets hosts allowlist.
   *
   * @return the hosts allowlist
   */
  public Collection<String> getHostsAllowlist() {
    return Collections.unmodifiableCollection(this.hostsAllowlist);
  }

  /**
   * Gets authentication public key file.
   *
   * @return the authentication public key file
   */
  public File getAuthenticationPublicKeyFile() {
    return authenticationPublicKeyFile;
  }

  /**
   * Sets authentication public key file.
   *
   * @param authenticationPublicKeyFile the authentication public key file
   */
  public void setAuthenticationPublicKeyFile(final File authenticationPublicKeyFile) {
    this.authenticationPublicKeyFile = authenticationPublicKeyFile;
  }

  /**
   * Gets authentication algorithm.
   *
   * @return the authentication algorithm
   */
  public JwtAlgorithm getAuthenticationAlgorithm() {
    return authenticationAlgorithm;
  }

  /**
   * Sets authentication algorithm.
   *
   * @param algorithm the algorithm
   */
  public void setAuthenticationAlgorithm(final JwtAlgorithm algorithm) {
    authenticationAlgorithm = algorithm;
  }

  /**
   * Gets timeout sec.
   *
   * @return the timeout sec
   */
  public long getTimeoutSec() {
    return timeoutSec;
  }

  /**
   * Sets timeout sec.
   *
   * @param timeoutSec the timeout sec
   */
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

  /**
   * Gets max active connections.
   *
   * @return the max active connections
   */
  public int getMaxActiveConnections() {
    return maxActiveConnections;
  }

  /**
   * Sets max active connections.
   *
   * @param maxActiveConnections the max active connections
   */
  public void setMaxActiveConnections(final int maxActiveConnections) {
    this.maxActiveConnections = maxActiveConnections;
  }

  /**
   * Sets max frame size.
   *
   * @param maxFrameSize the max frame size
   */
  public void setMaxFrameSize(final int maxFrameSize) {
    this.maxFrameSize = maxFrameSize;
  }

  /**
   * Gets max frame size.
   *
   * @return the max frame size
   */
  public Integer getMaxFrameSize() {
    return maxFrameSize;
  }
}
