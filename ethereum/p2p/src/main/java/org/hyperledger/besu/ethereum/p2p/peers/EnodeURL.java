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
package org.hyperledger.besu.ethereum.p2p.peers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.util.NetworkUtility;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.net.InetAddress;
import java.net.URI;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  private final OptionalInt listeningPort;
  private final OptionalInt discoveryPort;

  private EnodeURL(
      final BytesValue nodeId,
      final InetAddress address,
      final OptionalInt listeningPort,
      final OptionalInt discoveryPort) {
    checkArgument(
        nodeId.size() == NODE_ID_SIZE, "Invalid node id.  Expected id of length: 64 bytes.");
    checkArgument(
        !listeningPort.isPresent() || NetworkUtility.isValidPort(listeningPort.getAsInt()),
        "Invalid listening port.  Port should be between 1 - 65535.");
    checkArgument(
        !discoveryPort.isPresent() || NetworkUtility.isValidPort(discoveryPort.getAsInt()),
        "Invalid discovery port.  Port should be between 1 - 65535.");

    this.nodeId = nodeId;
    this.ip = address;
    this.listeningPort = listeningPort;
    this.discoveryPort = discoveryPort;
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

    final BytesValue id = BytesValue.fromHexString(uri.getUserInfo());
    String host = uri.getHost();
    int tcpPort = uri.getPort();

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
    } else {
      discoveryPort = OptionalInt.of(tcpPort);
    }

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

  public static boolean sameListeningEndpoint(final EnodeURL enodeA, final EnodeURL enodeB) {
    if (enodeA == null || enodeB == null) {
      return false;
    }

    return Objects.equals(enodeA.nodeId, enodeB.nodeId)
        && Objects.equals(enodeA.ip, enodeB.ip)
        && Objects.equals(enodeA.listeningPort, enodeB.listeningPort);
  }

  public static BytesValue parseNodeId(final String nodeId) {
    int expectedSize = EnodeURL.NODE_ID_SIZE * 2;
    if (nodeId.toLowerCase().startsWith("0x")) {
      expectedSize += 2;
    }
    checkArgument(
        nodeId.length() == expectedSize,
        "Expected " + EnodeURL.NODE_ID_SIZE + " bytes in " + nodeId);
    return BytesValue.fromHexString(nodeId, NODE_ID_SIZE);
  }

  public URI toURI() {
    final String uri =
        String.format(
            "enode://%s@%s:%d",
            nodeId.toUnprefixedString(), InetAddresses.toUriString(ip), getListeningPortOrZero());
    final OptionalInt discPort = getDiscPortQueryParam();
    if (discPort.isPresent()) {
      return URI.create(uri + String.format("?discport=%d", discPort.getAsInt()));
    } else {
      return URI.create(uri);
    }
  }

  public URI toURIWithoutDiscoveryPort() {
    final String uri =
        String.format(
            "enode://%s@%s:%d",
            nodeId.toUnprefixedString(), InetAddresses.toUriString(ip), getListeningPortOrZero());

    return URI.create(uri);
  }

  /**
   * Returns the discovery port only if it differs from the listening port
   *
   * @return
   */
  private OptionalInt getDiscPortQueryParam() {
    final int listeningPort = getListeningPortOrZero();
    final int discoveryPort = getDiscoveryPortOrZero();
    if (listeningPort == discoveryPort) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(discoveryPort);
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

  public boolean isListening() {
    return listeningPort.isPresent();
  }

  public boolean isRunningDiscovery() {
    return discoveryPort.isPresent();
  }

  public OptionalInt getListeningPort() {
    return listeningPort;
  }

  public int getListeningPortOrZero() {
    return listeningPort.orElse(0);
  }

  public OptionalInt getDiscoveryPort() {
    return discoveryPort;
  }

  public int getDiscoveryPortOrZero() {
    return discoveryPort.orElse(0);
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
    return Objects.equals(nodeId, enodeURL.nodeId)
        && Objects.equals(ip, enodeURL.ip)
        && Objects.equals(listeningPort, enodeURL.listeningPort)
        && Objects.equals(discoveryPort, enodeURL.discoveryPort);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId, ip, listeningPort, discoveryPort);
  }

  @Override
  public String toString() {
    return this.toURI().toString();
  }

  public static class Builder {

    private BytesValue nodeId;
    private OptionalInt listeningPort;
    private OptionalInt discoveryPort;
    private InetAddress ip;

    private Builder() {};

    public EnodeURL build() {
      validate();
      return new EnodeURL(nodeId, ip, listeningPort, discoveryPort);
    }

    private void validate() {
      checkState(nodeId != null, "Node id must be configured.");
      checkState(listeningPort != null, "Listening port must be configured.");
      checkState(discoveryPort != null, "Discovery port must be configured.");
      checkState(ip != null, "Ip address must be configured.");
    }

    public Builder configureFromEnode(final EnodeURL enode) {
      return this.nodeId(enode.getNodeId())
          .listeningPort(enode.getListeningPort())
          .discoveryPort(enode.getDiscoveryPort())
          .ipAddress(enode.getIp());
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

    public Builder discoveryAndListeningPorts(final int listeningAndDiscoveryPorts) {
      listeningPort(listeningAndDiscoveryPorts);
      discoveryPort(listeningAndDiscoveryPorts);
      return this;
    }

    public Builder disableListening() {
      this.listeningPort = OptionalInt.empty();
      return this;
    }

    public Builder disableDiscovery() {
      this.discoveryPort = OptionalInt.empty();
      return this;
    }

    public Builder useDefaultPorts() {
      discoveryAndListeningPorts(EnodeURL.DEFAULT_LISTENING_PORT);
      return this;
    }

    /**
     * An optional listening port value. If the value is empty of equal to 0, the listening port
     * will be empty - indicating the corresponding node is not listening.
     *
     * @param listeningPort If non-empty represents the port to listen on, if empty means the node
     *     is not listening
     * @return The modified builder
     */
    public Builder listeningPort(final OptionalInt listeningPort) {
      if (listeningPort.isPresent() && listeningPort.getAsInt() == 0) {
        this.listeningPort = OptionalInt.empty();
      } else {
        this.listeningPort = listeningPort;
      }
      return this;
    }

    /**
     * An listening port value. A value of 0 means the node is not listening.
     *
     * @param listeningPort If non-zero, represents the port on which to listen for connections. A
     *     value of 0 means the node is not listening for connections.
     * @return The modified builder
     */
    public Builder listeningPort(final int listeningPort) {
      if (listeningPort == 0) {
        this.listeningPort = OptionalInt.empty();
      } else {
        this.listeningPort = OptionalInt.of(listeningPort);
      }
      return this;
    }

    /**
     * The port on which to listen for discovery messages. A value that is empty or equal to 0,
     * indicates that the node is not listening for discovery messages.
     *
     * @param discoveryPort If non-empty and non-zero, represents the port on which to listen for
     *     discovery messages. Otherwise, indicates that the node is not running discovery.
     * @return The modified builder
     */
    public Builder discoveryPort(final OptionalInt discoveryPort) {
      if (discoveryPort.isPresent() && discoveryPort.getAsInt() == 0) {
        this.discoveryPort = OptionalInt.empty();
      } else {
        this.discoveryPort = discoveryPort;
      }

      return this;
    }

    /**
     * The port on which to listen for discovery messages. A value that is equal to 0, indicates
     * that the node is not listening for discovery messages.
     *
     * @param discoveryPort If non-zero, represents the port on which to listen for discovery
     *     messages. Otherwise, indicates that the node is not running discovery.
     * @return The modified builder
     */
    public Builder discoveryPort(final int discoveryPort) {
      if (discoveryPort == 0) {
        this.discoveryPort = OptionalInt.empty();
      } else {
        this.discoveryPort = OptionalInt.of(discoveryPort);
      }
      return this;
    }
  }
}
