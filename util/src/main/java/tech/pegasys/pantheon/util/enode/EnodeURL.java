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
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.net.InetAddress;
import java.net.URI;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Objects;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.Ints;

public class EnodeURL {

  public static final int DEFAULT_LISTENING_PORT = 30303;
  public static final int NODE_ID_SIZE = 64;
  private static final Pattern DISCPORT_QUERY_STRING_REGEX =
      Pattern.compile("^discport=([0-9]{1,5})$");
  private static final Pattern NODE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{128}$");

  private final BytesValue nodeId;
  private final InetAddress ip;
  private final Integer listeningPort;
  // DiscoveryPort will only be present if it differs from listening port, otherwise
  // the discovery port is assumed to match the listening port
  private final OptionalInt discoveryPort;

  private EnodeURL(
      final BytesValue nodeId,
      final InetAddress address,
      final Integer listeningPort,
      final OptionalInt discoveryPort) {
    checkArgument(
        nodeId.size() == NODE_ID_SIZE, "Invalid node id.  Expected id of length: 64 bytes.");
    checkArgument(
        NetworkUtility.isValidPort(listeningPort),
        "Invalid listening port.  Port should be between 1 - 65535.");
    checkArgument(
        !discoveryPort.isPresent() || NetworkUtility.isValidPort(discoveryPort.getAsInt()),
        "Invalid discovery port.  Port should be between 1 - 65535.");

    this.nodeId = nodeId;
    this.ip = address;
    this.listeningPort = listeningPort;
    // Only explicitly define a discovery port if it differs from the listening port
    if (discoveryPort.isPresent() && discoveryPort.getAsInt() != listeningPort) {
      this.discoveryPort = discoveryPort;
    } else {
      this.discoveryPort = OptionalInt.empty();
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static EnodeURL fromString(final String value) {
    try {
      checkStringArgumentNotEmpty(value, "Invalid empty value.");
      return fromURI(URI.create(value));
    } catch (IllegalArgumentException e) {
      String message =
          "Invalid enode URL syntax. Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.";
      if (e.getMessage() != null) {
        message += " " + e.getMessage();
      }
      throw new IllegalArgumentException(message, e);
    }
  }

  public static EnodeURL fromURI(final URI uri) {
    checkArgument(uri != null, "URI cannot be null");
    checkStringArgumentNotEmpty(uri.getScheme(), "Missing 'enode' scheme.");
    checkStringArgumentNotEmpty(uri.getHost(), "Missing or invalid ip address.");
    checkStringArgumentNotEmpty(uri.getUserInfo(), "Missing node ID.");

    checkArgument(
        uri.getScheme().equalsIgnoreCase("enode"), "Invalid URI scheme (must equal \"enode\").");
    checkArgument(
        NODE_ID_PATTERN.matcher(uri.getUserInfo()).matches(),
        "Invalid node ID: node ID must have exactly 128 hexadecimal characters and should not include any '0x' hex prefix.");

    // Parse discport if it exists
    OptionalInt discoveryPort = OptionalInt.empty();
    String query = uri.getQuery();
    if (query != null) {
      final Matcher discPortMatcher = DISCPORT_QUERY_STRING_REGEX.matcher(query);
      if (discPortMatcher.matches()) {
        Integer discPort = Ints.tryParse(discPortMatcher.group(1));
        discoveryPort = discPort == null ? discoveryPort : OptionalInt.of(discPort);
      }
      checkArgument(discoveryPort.isPresent(), "Invalid discovery port: '" + query + "'.");
    }

    final BytesValue id = BytesValue.fromHexString(uri.getUserInfo());
    String host = uri.getHost();
    int tcpPort = uri.getPort();

    return builder()
        .ipAddress(host)
        .nodeId(id)
        .listeningPort(tcpPort)
        .discoveryPort(discoveryPort)
        .build();
  }

  private static void checkStringArgumentNotEmpty(final String argument, final String message) {
    checkArgument(argument != null && !argument.trim().isEmpty(), message);
  }

  public URI toURI() {
    final String uri =
        String.format(
            "enode://%s@%s:%d",
            nodeId.toUnprefixedString(), InetAddresses.toUriString(ip), listeningPort);
    if (discoveryPort.isPresent()) {
      return URI.create(uri + String.format("?discport=%d", discoveryPort.getAsInt()));
    } else {
      return URI.create(uri);
    }
  }

  public static URI asURI(final String url) {
    return fromString(url).toURI();
  }

  public BytesValue getNodeId() {
    return nodeId;
  }

  public String getIpAsString() {
    return ip.getHostAddress();
  }

  public InetAddress getIp() {
    return ip;
  }

  public int getListeningPort() {
    return listeningPort;
  }

  public OptionalInt getDiscoveryPort() {
    return discoveryPort;
  }

  /**
   * @return Returns the discovery port if explicitly specified, otherwise returns the listening
   *     port.
   */
  public int getEffectiveDiscoveryPort() {
    return discoveryPort.orElse(listeningPort);
  }

  public boolean sameEndpoint(final EnodeURL enode) {
    return Objects.equal(nodeId, enode.nodeId)
        && Objects.equal(ip, enode.ip)
        && Objects.equal(listeningPort, enode.listeningPort);
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

  public static class Builder {

    private BytesValue nodeId;
    private Integer listeningPort = 30303;
    private OptionalInt discoveryPort = OptionalInt.empty();
    private InetAddress ip;

    private Builder() {};

    public EnodeURL build() {
      return new EnodeURL(nodeId, ip, listeningPort, discoveryPort);
    }

    public Builder nodeId(final BytesValue nodeId) {
      this.nodeId = nodeId;
      return this;
    }

    public Builder nodeId(final byte[] nodeId) {
      this.nodeId = BytesValue.wrap(nodeId);
      return this;
    }

    public Builder nodeId(final String nodeId) {
      this.nodeId = BytesValue.fromHexString(nodeId);
      return this;
    }

    public Builder ipAddress(final InetAddress ip) {
      this.ip = ip;
      return this;
    }

    public Builder ipAddress(final String ip) {
      if (InetAddresses.isUriInetAddress(ip)) {
        this.ip = InetAddresses.forUriString(ip);
      } else if (InetAddresses.isInetAddress(ip)) {
        this.ip = InetAddresses.forString(ip);
      } else {
        throw new IllegalArgumentException("Invalid ip address.");
      }
      return this;
    }

    public Builder listeningPort(final Integer listeningPort) {
      this.listeningPort = listeningPort;
      return this;
    }

    public Builder discoveryPort(final OptionalInt discoveryPort) {
      this.discoveryPort = discoveryPort;
      return this;
    }

    public Builder discoveryPort(final int discoveryPort) {
      this.discoveryPort = OptionalInt.of(discoveryPort);
      return this;
    }
  }
}
