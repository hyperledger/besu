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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.DEFAULT_RPC_APIS;

import org.hyperledger.besu.ethereum.api.handlers.TimeoutOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.JwtAlgorithm;
import org.hyperledger.besu.ethereum.api.tls.TlsConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;

/** The type Json rpc configuration. */
public class JsonRpcConfiguration {
  private static final String DEFAULT_JSON_RPC_HOST = "127.0.0.1";

  /** The constant DEFAULT_JSON_RPC_PORT. */
  public static final int DEFAULT_JSON_RPC_PORT = 8545;

  /** The constant DEFAULT_ENGINE_JSON_RPC_PORT. */
  public static final int DEFAULT_ENGINE_JSON_RPC_PORT = 8551;

  /** The constant DEFAULT_MAX_ACTIVE_CONNECTIONS. */
  public static final int DEFAULT_MAX_ACTIVE_CONNECTIONS = 80;

  /** The constant DEFAULT_MAX_BATCH_SIZE. */
  public static final int DEFAULT_MAX_BATCH_SIZE = 1024;

  /** The constant DEFAULT_MAX_REQUEST_CONTENT_LENGTH. */
  public static final long DEFAULT_MAX_REQUEST_CONTENT_LENGTH = 5 * 1024 * 1024; // 5MB

  /** The constant DEFAULT_PRETTY_JSON_ENABLED. */
  public static final boolean DEFAULT_PRETTY_JSON_ENABLED = false;

  private boolean enabled;
  private int port;
  private String host;
  private List<String> corsAllowedDomains = Collections.emptyList();
  private List<String> rpcApis;
  private List<String> noAuthRpcApis = Collections.emptyList();
  private List<String> hostsAllowlist = Arrays.asList("localhost", "127.0.0.1");
  private boolean authenticationEnabled = false;
  private String authenticationCredentialsFile;
  private JwtAlgorithm authenticationAlgorithm = JwtAlgorithm.RS256;
  private File authenticationPublicKeyFile;
  private Optional<TlsConfiguration> tlsConfiguration = Optional.empty();
  private long httpTimeoutSec = TimeoutOptions.defaultOptions().getTimeoutSeconds();
  private int maxActiveConnections;
  private int maxBatchSize;
  private long maxRequestContentLength;
  private boolean prettyJsonEnabled;

  /**
   * Create default json rpc configuration.
   *
   * @return the json rpc configuration
   */
  public static JsonRpcConfiguration createDefault() {
    final JsonRpcConfiguration config = new JsonRpcConfiguration();
    config.setEnabled(false);
    config.setPort(DEFAULT_JSON_RPC_PORT);
    config.setHost(DEFAULT_JSON_RPC_HOST);
    config.setRpcApis(DEFAULT_RPC_APIS);
    config.httpTimeoutSec = TimeoutOptions.defaultOptions().getTimeoutSeconds();
    config.setMaxActiveConnections(DEFAULT_MAX_ACTIVE_CONNECTIONS);
    config.setMaxBatchSize(DEFAULT_MAX_BATCH_SIZE);
    config.setMaxRequestContentLength(DEFAULT_MAX_REQUEST_CONTENT_LENGTH);
    config.setPrettyJsonEnabled(DEFAULT_PRETTY_JSON_ENABLED);
    return config;
  }

  /**
   * Create engine default json rpc configuration.
   *
   * @return the json rpc configuration
   */
  public static JsonRpcConfiguration createEngineDefault() {
    final JsonRpcConfiguration config = createDefault();
    config.setEnabled(false);
    config.setPort(DEFAULT_ENGINE_JSON_RPC_PORT);
    List<String> engineMethodGroup = new ArrayList<>(2);
    engineMethodGroup.add(RpcApis.ENGINE.name());
    engineMethodGroup.add(RpcApis.ETH.name());
    config.setRpcApis(engineMethodGroup);
    config.setAuthenticationEnabled(true);
    config.setAuthenticationAlgorithm(JwtAlgorithm.HS256);
    config.setAuthenticationPublicKeyFile(null); // ephemeral key will be generated on startup.
    return config;
  }

  private JsonRpcConfiguration() {}

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
   * Gets port.
   *
   * @return the port
   */
  public int getPort() {
    return port;
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
   * Gets host.
   *
   * @return the host
   */
  public String getHost() {
    return host;
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
   * Gets cors allowed domains.
   *
   * @return the cors allowed domains
   */
  public Collection<String> getCorsAllowedDomains() {
    return corsAllowedDomains;
  }

  /**
   * Sets cors allowed domains.
   *
   * @param corsAllowedDomains the cors allowed domains
   */
  public void setCorsAllowedDomains(final List<String> corsAllowedDomains) {
    if (corsAllowedDomains != null) {
      this.corsAllowedDomains = corsAllowedDomains;
    }
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
   * Gets no auth rpc apis.
   *
   * @return the no auth rpc apis
   */
  public Collection<String> getNoAuthRpcApis() {
    return this.noAuthRpcApis;
  }

  /**
   * Sets no auth rpc apis.
   *
   * @param rpcApis the rpc apis
   */
  public void setNoAuthRpcApis(final List<String> rpcApis) {
    this.noAuthRpcApis = rpcApis;
  }

  /**
   * Add rpc api.
   *
   * @param rpcApi the rpc api
   */
  public void addRpcApi(final String rpcApi) {
    this.rpcApis = new ArrayList<>(rpcApis);
    rpcApis.add(rpcApi);
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
   * Sets hosts allowlist.
   *
   * @param hostsAllowlist the hosts allowlist
   */
  public void setHostsAllowlist(final List<String> hostsAllowlist) {
    this.hostsAllowlist = hostsAllowlist;
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
   * @param authenticationAlgorithm the authentication algorithm
   */
  public void setAuthenticationAlgorithm(final JwtAlgorithm authenticationAlgorithm) {
    this.authenticationAlgorithm = authenticationAlgorithm;
  }

  /**
   * Gets tls configuration.
   *
   * @return the tls configuration
   */
  public Optional<TlsConfiguration> getTlsConfiguration() {
    return tlsConfiguration;
  }

  /**
   * Sets tls configuration.
   *
   * @param tlsConfiguration the tls configuration
   */
  public void setTlsConfiguration(final Optional<TlsConfiguration> tlsConfiguration) {
    this.tlsConfiguration = tlsConfiguration;
  }

  /**
   * Gets http timeout sec.
   *
   * @return the http timeout sec
   */
  public long getHttpTimeoutSec() {
    return httpTimeoutSec;
  }

  /**
   * Sets http timeout sec.
   *
   * @param httpTimeoutSec the http timeout sec
   */
  public void setHttpTimeoutSec(final long httpTimeoutSec) {
    this.httpTimeoutSec = httpTimeoutSec;
  }

  /**
   * Is pretty json enabled boolean.
   *
   * @return the boolean
   */
  public boolean isPrettyJsonEnabled() {
    return prettyJsonEnabled;
  }

  /**
   * Sets pretty json enabled.
   *
   * @param prettyJsonEnabled the pretty json enabled
   */
  public void setPrettyJsonEnabled(final boolean prettyJsonEnabled) {
    this.prettyJsonEnabled = prettyJsonEnabled;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("enabled", enabled)
        .add("port", port)
        .add("host", host)
        .add("corsAllowedDomains", corsAllowedDomains)
        .add("hostsAllowlist", hostsAllowlist)
        .add("rpcApis", rpcApis)
        .add("authenticationEnabled", authenticationEnabled)
        .add("authenticationCredentialsFile", authenticationCredentialsFile)
        .add("authenticationPublicKeyFile", authenticationPublicKeyFile)
        .add("tlsConfiguration", tlsConfiguration)
        .add("httpTimeoutSec", httpTimeoutSec)
        .add("maxActiveConnections", maxActiveConnections)
        .add("maxBatchSize", maxBatchSize)
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
        && authenticationEnabled == that.authenticationEnabled
        && Objects.equals(host, that.host)
        && Objects.equals(corsAllowedDomains, that.corsAllowedDomains)
        && Objects.equals(rpcApis, that.rpcApis)
        && Objects.equals(hostsAllowlist, that.hostsAllowlist)
        && Objects.equals(authenticationCredentialsFile, that.authenticationCredentialsFile)
        && Objects.equals(authenticationPublicKeyFile, that.authenticationPublicKeyFile)
        && maxBatchSize == that.maxBatchSize;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        enabled,
        port,
        host,
        corsAllowedDomains,
        rpcApis,
        hostsAllowlist,
        authenticationEnabled,
        authenticationCredentialsFile,
        authenticationPublicKeyFile,
        maxBatchSize);
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
   * Gets max batch size.
   *
   * @return the max batch size
   */
  public int getMaxBatchSize() {
    return maxBatchSize;
  }

  /**
   * Sets max batch size.
   *
   * @param maxBatchSize the max batch size
   */
  public void setMaxBatchSize(final int maxBatchSize) {
    this.maxBatchSize = maxBatchSize;
  }

  /**
   * Gets max request content length.
   *
   * @return the max request content length
   */
  public long getMaxRequestContentLength() {
    return maxRequestContentLength;
  }

  /**
   * Sets max request content length.
   *
   * @param maxRequestContentLength the max request content length
   */
  public void setMaxRequestContentLength(final long maxRequestContentLength) {
    this.maxRequestContentLength = maxRequestContentLength;
  }
}
