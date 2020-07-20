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
package org.hyperledger.besu.ethstats.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.primitives.Ints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NetstatsUrl {

  private static final Logger LOG = LogManager.getLogger();

  public static final Pattern NETSTATS_URL_REGEX =
      Pattern.compile("([-\\w]+):([\\w]+)?@([.\\w]+):([\\d]+)");

  private final String nodeName;
  private final String secret;
  private final String host;
  private final Integer port;
  private final String contact;

  public NetstatsUrl(
      final String nodeName,
      final String secret,
      final String host,
      final Integer port,
      final String contact) {
    this.nodeName = nodeName;
    this.secret = secret;
    this.host = host;
    this.port = port;
    this.contact = contact;
  }

  public String getNodeName() {
    return nodeName;
  }

  public String getSecret() {
    return secret;
  }

  public String getHost() {
    return host;
  }

  public Integer getPort() {
    return port;
  }

  public String getContact() {
    return contact;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static NetstatsUrl fromParams(final String value, final String contact) {
    try {
      checkArgument(value != null && !value.trim().isEmpty(), "Invalid empty value.");

      final Matcher netStatsUrl = NETSTATS_URL_REGEX.matcher(value);
      if (netStatsUrl.matches()) {
        return NetstatsUrl.builder()
            .nodeName(netStatsUrl.group(1))
            .secret(netStatsUrl.group(2))
            .host(netStatsUrl.group(3))
            .port(Ints.tryParse(netStatsUrl.group(4)))
            .contact(contact)
            .build();
      }

    } catch (IllegalArgumentException e) {
      LOG.error(e.getMessage());
    }
    throw new IllegalArgumentException(
        "Invalid netstats URL syntax. Netstats URL should have the following format 'nodename:secret@host:port'.");
  }

  @Override
  public String toString() {
    return "org.hyperledger.besu.ethstats.util.NetstatsUrl{"
        + "nodeName='"
        + nodeName
        + '\''
        + ", secret='"
        + secret
        + '\''
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + '}';
  }

  public static class Builder {

    private String nodeName;
    private String secret;
    private String host;
    private Integer port;
    private String contact;

    private Builder() {};

    public NetstatsUrl build() {
      validate();
      return new NetstatsUrl(nodeName, secret, host, port, contact);
    }

    private void validate() {
      checkState(nodeName != null, "Node name must be configured.");
      checkState(secret != null, "Secret must be configured.");
      checkState(host != null, "Host must be configured.");
      checkState(port != null, "Port must be configured.");
      checkState(contact != null, "Contact must be configured.");
    }

    public Builder nodeName(final String nodeName) {
      this.nodeName = nodeName;
      return this;
    }

    public Builder secret(final String secret) {
      this.secret = secret;
      return this;
    }

    public Builder host(final String host) {
      this.host = host;
      return this;
    }

    public Builder port(final int port) {
      this.port = port;
      return this;
    }

    public Builder contact(final String contact) {
      this.contact = contact;
      return this;
    }
  }
}
