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

import java.net.InetAddress;
import java.net.URI;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Objects;
import com.google.common.net.InetAddresses;

public class EnodeURL {

  private static final String HEX_STRING_PATTERN = "[0-9a-fA-F]+";

  private static final String ENODE_URL_PATTERN_NEW =
      "^enode://"
          + "(?<nodeId>\\w+)"
          + "@"
          + "(?<ip>.*)"
          + ":"
          + "(?<listening>\\d+)"
          + "(\\?discport=(?<discovery>\\d+))?$";

  private final String nodeId;
  private final InetAddress ip;
  private final Integer listeningPort;
  private final OptionalInt discoveryPort;

  public EnodeURL(
      final String nodeId,
      final String ip,
      final Integer listeningPort,
      final OptionalInt discoveryPort) {
    this(nodeId, InetAddresses.forUriString(ip), listeningPort, discoveryPort);
  }

  public EnodeURL(final String nodeId, final String ip, final Integer listeningPort) {
    this(nodeId, ip, listeningPort, OptionalInt.empty());
  }

  public EnodeURL(final String nodeId, final InetAddress address, final Integer listeningPort) {
    this(nodeId, address, listeningPort, OptionalInt.empty());
  }

  public EnodeURL(
      final String nodeId,
      final InetAddress address,
      final Integer listeningPort,
      final OptionalInt discoveryPort) {
    if (nodeId.startsWith("0x")) {
      this.nodeId = nodeId.substring(2);
    } else {
      this.nodeId = nodeId;
    }
    this.ip = address;
    this.listeningPort = listeningPort;
    this.discoveryPort = discoveryPort;
  }

  public EnodeURL(final String value) {
    checkArgument(
        value != null && !value.isEmpty(), "Can't convert null/empty string to EnodeURLProperty.");

    final Matcher enodeMatcher = Pattern.compile(ENODE_URL_PATTERN_NEW).matcher(value);
    checkArgument(
        enodeMatcher.matches(),
        "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.");

    this.nodeId = getAndValidateNodeId(enodeMatcher);
    this.ip = getAndValidateIp(enodeMatcher);
    this.listeningPort = getAndValidatePort(enodeMatcher, "listening");
    this.discoveryPort = getAndValidateDiscoveryPort(enodeMatcher);
  }

  public URI toURI() {
    final String uri =
        String.format("enode://%s@%s:%d", nodeId, InetAddresses.toUriString(ip), listeningPort);
    if (discoveryPort.isPresent()) {
      return URI.create(uri + String.format("?discport=%d", discoveryPort.getAsInt()));
    } else {
      return URI.create(uri);
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

  private static InetAddress getAndValidateIp(final Matcher matcher) {
    final String ipString = matcher.group("ip");

    try {
      return InetAddresses.forUriString(ipString);
    } catch (IllegalArgumentException e) {
      if (e.getMessage().contains("Not a valid URI IP literal: ")) {
        throw new IllegalArgumentException("Invalid enode URL IP format.");
      } else {
        throw e;
      }
    }
  }

  private static Integer getAndValidatePort(final Matcher matcher, final String portName) {
    int port = Integer.valueOf(matcher.group(portName));
    checkArgument(
        NetworkUtility.isValidPort(port),
        "Invalid " + portName + " port range. Port should be between 0 - 65535");
    return port;
  }

  private static OptionalInt getAndValidateDiscoveryPort(final Matcher matcher) {
    if (matcher.group("discovery") != null) {
      return OptionalInt.of(getAndValidatePort(matcher, "discovery"));
    } else {
      return OptionalInt.empty();
    }
  }

  public String getNodeId() {
    return nodeId;
  }

  public String getIp() {
    return ip.getHostAddress();
  }

  public InetAddress getInetAddress() {
    return ip;
  }

  public Integer getListeningPort() {
    return listeningPort;
  }

  public OptionalInt getDiscoveryPort() {
    return discoveryPort;
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

  @Override
  public String toString() {
    return this.toURI().toString();
  }
}
