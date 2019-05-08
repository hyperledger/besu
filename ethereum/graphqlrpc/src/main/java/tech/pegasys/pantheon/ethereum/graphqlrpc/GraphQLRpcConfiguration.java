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
package tech.pegasys.pantheon.ethereum.graphqlrpc;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

public class GraphQLRpcConfiguration {
  private static final String DEFAULT_GRAPHQL_RPC_HOST = "127.0.0.1";
  public static final int DEFAULT_GRAPHQL_RPC_PORT = 8547;

  private boolean enabled;
  private int port;
  private String host;
  private Collection<String> corsAllowedDomains = Collections.emptyList();
  private Collection<String> hostsWhitelist = Arrays.asList("localhost", "127.0.0.1");

  public static GraphQLRpcConfiguration createDefault() {
    final GraphQLRpcConfiguration config = new GraphQLRpcConfiguration();
    config.setEnabled(false);
    config.setPort(DEFAULT_GRAPHQL_RPC_PORT);
    config.setHost(DEFAULT_GRAPHQL_RPC_HOST);
    return config;
  }

  private GraphQLRpcConfiguration() {}

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

  Collection<String> getCorsAllowedDomains() {
    return corsAllowedDomains;
  }

  public void setCorsAllowedDomains(final Collection<String> corsAllowedDomains) {
    checkNotNull(corsAllowedDomains);
    this.corsAllowedDomains = corsAllowedDomains;
  }

  Collection<String> getHostsWhitelist() {
    return Collections.unmodifiableCollection(this.hostsWhitelist);
  }

  public void setHostsWhitelist(final Collection<String> hostsWhitelist) {
    checkNotNull(hostsWhitelist);
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
    final GraphQLRpcConfiguration that = (GraphQLRpcConfiguration) o;
    return enabled == that.enabled
        && port == that.port
        && Objects.equal(host, that.host)
        && Objects.equal(
            Lists.newArrayList(corsAllowedDomains), Lists.newArrayList(that.corsAllowedDomains))
        && Objects.equal(
            Lists.newArrayList(hostsWhitelist), Lists.newArrayList(that.hostsWhitelist));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(enabled, port, host, corsAllowedDomains, hostsWhitelist);
  }
}
