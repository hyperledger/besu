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

public class GraphQLConfiguration {
  private static final String DEFAULT_GRAPHQL_HTTP_HOST = "127.0.0.1";
  public static final int DEFAULT_GRAPHQL_HTTP_PORT = 8547;

  private boolean enabled;
  private int port;
  private String host;
  private List<String> corsAllowedDomains = Collections.emptyList();
  private List<String> hostsAllowlist = Arrays.asList("localhost", "127.0.0.1");
  private long httpTimeoutSec = TimeoutOptions.defaultOptions().getTimeoutSeconds();

  public static GraphQLConfiguration createDefault() {
    final GraphQLConfiguration config = new GraphQLConfiguration();
    config.setEnabled(false);
    config.setPort(DEFAULT_GRAPHQL_HTTP_PORT);
    config.setHost(DEFAULT_GRAPHQL_HTTP_HOST);
    config.setHttpTimeoutSec(TimeoutOptions.defaultOptions().getTimeoutSeconds());
    return config;
  }

  private GraphQLConfiguration() {}

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

  public void setCorsAllowedDomains(final List<String> corsAllowedDomains) {
    checkNotNull(corsAllowedDomains);
    this.corsAllowedDomains = corsAllowedDomains;
  }

  Collection<String> getHostsAllowlist() {
    return Collections.unmodifiableCollection(this.hostsAllowlist);
  }

  public void setHostsAllowlist(final List<String> hostsAllowlist) {
    checkNotNull(hostsAllowlist);
    this.hostsAllowlist = hostsAllowlist;
  }

  public Long getHttpTimeoutSec() {
    return httpTimeoutSec;
  }

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
