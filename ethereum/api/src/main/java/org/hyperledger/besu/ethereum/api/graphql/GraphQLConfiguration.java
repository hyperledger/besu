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
package org.hyperledger.besu.ethereum.api.graphql;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.ethereum.api.handlers.TimeoutOptions;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.common.base.MoreObjects;

/**
 * Represents the configuration for GraphQL. This class is used to set and get the configuration
 * details for GraphQL such as enabling GraphQL, setting the port and host, setting the allowed
 * domains for CORS, setting the hosts allowlist, and setting the HTTP timeout.
 */
public class GraphQLConfiguration {
  private static final String DEFAULT_GRAPHQL_HTTP_HOST = "127.0.0.1";

  /** The default port number for the GraphQL HTTP server. */
  public static final int DEFAULT_GRAPHQL_HTTP_PORT = 8547;

  private boolean enabled;
  private int port;
  private String host;
  private List<String> corsAllowedDomains = Collections.emptyList();
  private List<String> hostsAllowlist = Arrays.asList("localhost", DEFAULT_GRAPHQL_HTTP_HOST);
  private long httpTimeoutSec = TimeoutOptions.defaultOptions().getTimeoutSeconds();

  /**
   * Creates a default configuration for GraphQL.
   *
   * <p>This method initializes a new GraphQLConfiguration object with default settings. The default
   * settings are: - GraphQL is not enabled - The port is set to the default GraphQL HTTP port - The
   * host is set to the default GraphQL HTTP host - The HTTP timeout is set to the default timeout
   *
   * @return a GraphQLConfiguration object with default settings
   */
  public static GraphQLConfiguration createDefault() {
    final GraphQLConfiguration config = new GraphQLConfiguration();
    config.setEnabled(false);
    config.setPort(DEFAULT_GRAPHQL_HTTP_PORT);
    config.setHost(DEFAULT_GRAPHQL_HTTP_HOST);
    config.setHttpTimeoutSec(TimeoutOptions.defaultOptions().getTimeoutSeconds());
    return config;
  }

  private GraphQLConfiguration() {}

  /**
   * Checks if GraphQL is enabled.
   *
   * @return true if GraphQL is enabled, false otherwise
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets the enabled status of GraphQL.
   *
   * @param enabled the status to set. true to enable GraphQL, false to disable it
   */
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Retrieves the port number for the GraphQL HTTP server.
   *
   * @return the port number
   */
  public int getPort() {
    return port;
  }

  /**
   * Sets the port number for the GraphQL HTTP server.
   *
   * @param port the port number to set
   */
  public void setPort(final int port) {
    this.port = port;
  }

  /**
   * Retrieves the host for the GraphQL HTTP server.
   *
   * @return the host
   */
  public String getHost() {
    return host;
  }

  /**
   * Sets the host for the GraphQL HTTP server.
   *
   * @param host the host to set
   */
  public void setHost(final String host) {
    this.host = host;
  }

  /**
   * Retrieves the allowed domains for CORS.
   *
   * @return a collection of allowed domains for CORS
   */
  Collection<String> getCorsAllowedDomains() {
    return corsAllowedDomains;
  }

  /**
   * Sets the allowed domains for CORS.
   *
   * @param corsAllowedDomains a list of allowed domains for CORS
   */
  public void setCorsAllowedDomains(final List<String> corsAllowedDomains) {
    checkNotNull(corsAllowedDomains);
    this.corsAllowedDomains = corsAllowedDomains;
  }

  /**
   * Retrieves the hosts allowlist.
   *
   * @return a collection of hosts in the allowlist
   */
  Collection<String> getHostsAllowlist() {
    return Collections.unmodifiableCollection(this.hostsAllowlist);
  }

  /**
   * Sets the hosts allowlist.
   *
   * @param hostsAllowlist a list of hosts to be added to the allowlist
   */
  public void setHostsAllowlist(final List<String> hostsAllowlist) {
    checkNotNull(hostsAllowlist);
    this.hostsAllowlist = hostsAllowlist;
  }

  /**
   * Retrieves the HTTP timeout in seconds.
   *
   * @return the HTTP timeout in seconds
   */
  public Long getHttpTimeoutSec() {
    return httpTimeoutSec;
  }

  /**
   * Sets the HTTP timeout in seconds.
   *
   * @param httpTimeoutSec the HTTP timeout to set in seconds
   */
  public void setHttpTimeoutSec(final long httpTimeoutSec) {
    this.httpTimeoutSec = httpTimeoutSec;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("enabled", enabled)
        .add("port", port)
        .add("host", host)
        .add("corsAllowedDomains", corsAllowedDomains)
        .add("hostsAllowlist", hostsAllowlist)
        .add("httpTimeoutSec", httpTimeoutSec)
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
    final GraphQLConfiguration that = (GraphQLConfiguration) o;
    return enabled == that.enabled
        && port == that.port
        && Objects.equals(host, that.host)
        && Objects.equals(corsAllowedDomains, that.corsAllowedDomains)
        && Objects.equals(hostsAllowlist, that.hostsAllowlist);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, port, host, corsAllowedDomains, hostsAllowlist);
  }
}
