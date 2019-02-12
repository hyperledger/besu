/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.util.enode;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.util.NetworkUtility;

import java.net.URI;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Objects;

public class EnodeURL {

  private static final String IP_REPLACE_MARKER = "$$IP_PATTERN$$";
  private static final String IPV4_PATTERN =
      "(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}";
  private static final String IPV6_PATTERN = "\\[(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\]";
  private static final String IPV6_COMPACT_PATTERN =
      "\\[((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)\\]";
  private static final String DISCOVERY_PORT_PATTERN = "\\?discport=(?<discovery>\\d+)";
  private static final String HEX_STRING_PATTERN = "[0-9a-fA-F]+";

  private static final String ENODE_URL_PATTERN =
      "enode://(?<nodeId>\\w+)@(?<ip>" + IP_REPLACE_MARKER + "):(?<listening>\\d+)";

  private final String nodeId;
  private final String ip;
  private final Integer listeningPort;
  private final OptionalInt discoveryPort;

  public EnodeURL(
      final String nodeId,
      final String ip,
      final Integer listeningPort,
      final OptionalInt discoveryPort) {
    this.nodeId = nodeId;
    this.ip = ip;
    this.listeningPort = listeningPort;
    this.discoveryPort = discoveryPort;
  }

  public EnodeURL(final String nodeId, final String ip, final Integer listeningPort) {
    this.nodeId = nodeId;
    this.ip = ip;
    this.listeningPort = listeningPort;
    this.discoveryPort = OptionalInt.empty();
  }

  public EnodeURL(final String value) {
    checkArgument(
        value != null && !value.isEmpty(), "Can't convert null/empty string to EnodeURLProperty.");

    final boolean containsDiscoveryPort = value.contains("discport");
    final boolean isIPV4 = Pattern.compile(".*" + IPV4_PATTERN + ".*").matcher(value).matches();
    final boolean isIPV6 = Pattern.compile(".*" + IPV6_PATTERN + ".*").matcher(value).matches();
    final boolean isIPV6Compact =
        Pattern.compile(".*" + IPV6_COMPACT_PATTERN + ".*").matcher(value).matches();

    String pattern = ENODE_URL_PATTERN;
    if (isIPV4) {
      pattern = pattern.replace(IP_REPLACE_MARKER, IPV4_PATTERN);
    } else if (isIPV6) {
      pattern = pattern.replace(IP_REPLACE_MARKER, IPV6_PATTERN);
    } else if (isIPV6Compact) {
      pattern = pattern.replace(IP_REPLACE_MARKER, IPV6_COMPACT_PATTERN);
    } else {
      throw new IllegalArgumentException("Invalid enode URL IP format.");
    }

    if (containsDiscoveryPort) {
      pattern += DISCOVERY_PORT_PATTERN;
    }
    if (isIPV6) {
      pattern = pattern.replace(IP_REPLACE_MARKER, IPV6_PATTERN);
    } else {
      pattern = pattern.replace(IP_REPLACE_MARKER, IPV4_PATTERN);
    }

    final Matcher matcher = Pattern.compile(pattern).matcher(value);
    checkArgument(
        matcher.matches(),
        "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.");

    this.nodeId = getAndValidateNodeId(matcher);
    this.ip = matcher.group("ip");
    this.listeningPort = getAndValidatePort(matcher, "listening");

    if (containsDiscoveryPort(value)) {
      this.discoveryPort = OptionalInt.of(getAndValidatePort(matcher, "discovery"));
    } else {
      this.discoveryPort = OptionalInt.empty();
    }
  }

  public URI toURI() {
    if (discoveryPort.isPresent()) {
      return URI.create(
          String.format(
              "enode://%s@%s:%d?discport=%d", nodeId, ip, listeningPort, discoveryPort.getAsInt()));
    } else {
      return URI.create(String.format("enode://%s@%s:%d", nodeId, ip, listeningPort));
    }
  }

  public static URI asURI(final String url) {
    return new EnodeURL(url).toURI();
  }

  private static String getAndValidateNodeId(final Matcher matcher) {
    final String invalidNodeIdErrorMsg =
        "Enode URL contains an invalid node ID. Node ID must have 128 characters and shouldn't include the '0x' hex prefix.";
    final String nodeId = matcher.group("nodeId");

    checkArgument(nodeId.matches(HEX_STRING_PATTERN), invalidNodeIdErrorMsg);
    checkArgument(nodeId.length() == 128, invalidNodeIdErrorMsg);

    return nodeId;
  }

  private static Integer getAndValidatePort(final Matcher matcher, final String portName) {
    int port = Integer.valueOf(matcher.group(portName));
    checkArgument(
        NetworkUtility.isValidPort(port),
        "Invalid " + portName + " port range. Port should be between 0 - 65535");
    return port;
  }

  private static boolean containsDiscoveryPort(final String value) {
    return value.contains("discport");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EnodeURL enodeURL = (EnodeURL) o;
    return Objects.equal(nodeId, enodeURL.nodeId)
        && Objects.equal(ip, enodeURL.ip)
        && Objects.equal(listeningPort, enodeURL.listeningPort)
        && Objects.equal(discoveryPort, enodeURL.discoveryPort);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(nodeId, ip, listeningPort, discoveryPort);
  }
}
