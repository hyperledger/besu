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
package tech.pegasys.pantheon.ethereum.api.jsonrpc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.common.base.MoreObjects;

public class JsonRpcConfiguration {
  private static final String DEFAULT_JSON_RPC_HOST = "127.0.0.1";
  public static final int DEFAULT_JSON_RPC_PORT = 8545;

  private boolean enabled;
  private int port;
  private String host;
  private List<String> corsAllowedDomains = Collections.emptyList();
  private List<RpcApi> rpcApis;
  private List<String> hostsWhitelist = Arrays.asList("localhost", "127.0.0.1");
  private boolean authenticationEnabled = false;
  private String authenticationCredentialsFile;

  public static JsonRpcConfiguration createDefault() {
    final JsonRpcConfiguration config = new JsonRpcConfiguration();
    config.setEnabled(false);
    config.setPort(DEFAULT_JSON_RPC_PORT);
    config.setHost(DEFAULT_JSON_RPC_HOST);
    config.rpcApis = RpcApis.DEFAULT_JSON_RPC_APIS;
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

  public void setCorsAllowedDomains(final List<String> corsAllowedDomains) {
    if (corsAllowedDomains != null) {
      this.corsAllowedDomains = corsAllowedDomains;
    }
  }

  public Collection<RpcApi> getRpcApis() {
    return rpcApis;
  }

  public void setRpcApis(final List<RpcApi> rpcApis) {
    this.rpcApis = rpcApis;
  }

  public void addRpcApi(final RpcApi rpcApi) {
    this.rpcApis = new ArrayList<>(rpcApis);
    rpcApis.add(rpcApi);
  }

  public Collection<String> getHostsWhitelist() {
    return Collections.unmodifiableCollection(this.hostsWhitelist);
  }

  public void setHostsWhitelist(final List<String> hostsWhitelist) {
    this.hostsWhitelist = hostsWhitelist;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("enabled", enabled)
        .add("port", port)
        .add("host", host)
        .add("corsAllowedDomains", corsAllowedDomains)
        .add("hostsWhitelist", hostsWhitelist)
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
    final JsonRpcConfiguration that = (JsonRpcConfiguration) o;
    return enabled == that.enabled
        && port == that.port
        && Objects.equals(host, that.host)
        && Objects.equals(corsAllowedDomains, that.corsAllowedDomains)
        && Objects.equals(hostsWhitelist, that.hostsWhitelist)
        && Objects.equals(rpcApis, that.rpcApis);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, port, host, corsAllowedDomains, hostsWhitelist, rpcApis);
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
}
