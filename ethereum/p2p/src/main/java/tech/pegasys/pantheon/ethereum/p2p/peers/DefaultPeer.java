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
package tech.pegasys.pantheon.ethereum.p2p.peers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.pantheon.util.Preconditions.checkGuard;

import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryPacketDecodingException;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.NetworkUtility;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.net.InetAddresses;
import com.google.common.primitives.Ints;

/** The default, basic representation of an Ethereum {@link Peer}. */
public class DefaultPeer extends DefaultPeerId implements Peer {

  public static final int PEER_ID_SIZE = 64;

  public static final int DEFAULT_PORT = 30303;
  private static final Pattern DISCPORT_QUERY_STRING_REGEX =
      Pattern.compile("discport=([0-9]{1,5})");

  private final Endpoint endpoint;

  public static DefaultPeer fromEnodeURL(final EnodeURL enodeURL) {
    final int udpPort = enodeURL.getDiscoveryPort().orElse(enodeURL.getListeningPort());

    final Endpoint endpoint =
        new Endpoint(
            enodeURL.getInetAddress().getHostAddress(),
            udpPort,
            OptionalInt.of(enodeURL.getListeningPort()));

    return new DefaultPeer(enodeURL.getNodeId(), endpoint);
  }

  /**
   * Creates a {@link DefaultPeer} instance from a String representation of an enode URL.
   *
   * @param uri A String representation of the enode URI.
   * @return The Peer instance.
   * @see <a href="https://github.com/ethereum/wiki/wiki/enode-url-format">enode URL format</a>
   */
  public static DefaultPeer fromURI(final String uri) {
    return fromURI(URI.create(uri));
  }

  /**
   * Creates a {@link DefaultPeer} instance from an URI object that follows the enode URL format.
   *
   * @param uri The enode URI.
   * @return The Peer instance.
   * @see <a href="https://github.com/ethereum/wiki/wiki/enode-url-format">enode URL format</a>
   */
  public static DefaultPeer fromURI(final URI uri) {
    checkNotNull(uri);
    checkArgument("enode".equals(uri.getScheme()));
    checkArgument(uri.getUserInfo() != null, "node id cannot be null");

    // Process the peer's public key, in the host portion of the URI.
    final BytesValue id = BytesValue.fromHexString(uri.getUserInfo());

    // Process the host.  If we have an IPv6 address in URL form translate to an address only form.
    String host = uri.getHost();
    if (!InetAddresses.isInetAddress(host) && InetAddresses.isUriInetAddress(host)) {
      host = InetAddresses.toAddrString(InetAddresses.forUriString(host));
    }

    // Process the ports; falling back to the default port in both TCP and UDP.
    int tcpPort = DEFAULT_PORT;
    int udpPort = DEFAULT_PORT;
    if (NetworkUtility.isValidPort(uri.getPort())) {
      tcpPort = udpPort = uri.getPort();
    }

    // If TCP and UDP ports differ, expect a query param 'discport' with the UDP port.
    // See https://github.com/ethereum/wiki/wiki/enode-url-format
    if (uri.getQuery() != null) {
      udpPort = extractUdpPortFromQuery(uri.getQuery()).orElse(tcpPort);
    }

    final Endpoint endpoint = new Endpoint(host, udpPort, OptionalInt.of(tcpPort));
    return new DefaultPeer(id, endpoint);
  }

  /**
   * Creates a {@link DefaultPeer} instance from its attributes, with a TCP port.
   *
   * @param id The node ID (public key).
   * @param host Ip address.
   * @param udpPort The UDP port.
   * @param tcpPort The TCP port.
   */
  public DefaultPeer(final BytesValue id, final String host, final int udpPort, final int tcpPort) {
    this(id, host, udpPort, OptionalInt.of(tcpPort));
  }

  /**
   * Creates a {@link DefaultPeer} instance from its attributes, without a TCP port.
   *
   * @param id The node ID (public key).
   * @param host Ip address.
   * @param udpPort UDP port.
   */
  public DefaultPeer(final BytesValue id, final String host, final int udpPort) {
    this(id, host, udpPort, OptionalInt.empty());
  }

  /**
   * Creates a {@link DefaultPeer} instance from its attributes, without a TCP port.
   *
   * @param id The node ID (public key).
   * @param host Ip address.
   * @param udpPort the port number on which to communicate UDP traffic with the peer.
   * @param tcpPort the port number on which to communicate TCP traffic with the peer.
   */
  public DefaultPeer(
      final BytesValue id, final String host, final int udpPort, final OptionalInt tcpPort) {
    this(id, new Endpoint(host, udpPort, tcpPort));
  }

  /**
   * Creates a {@link DefaultPeer} instance from its ID and its {@link Endpoint}.
   *
   * @param id The node ID (public key).
   * @param endpoint The endpoint for this peer.
   */
  public DefaultPeer(final BytesValue id, final Endpoint endpoint) {
    super(id);
    checkArgument(
        id != null && id.size() == PEER_ID_SIZE, "id must be non-null and exactly 64 bytes long");
    checkArgument(endpoint != null, "endpoint cannot be null");
    this.endpoint = endpoint;
  }

  /**
   * Decodes the RLP stream as a Peer instance.
   *
   * @param in The RLP input stream from which to read.
   * @return The decoded representation.
   */
  public static Peer readFrom(final RLPInput in) {
    final int size = in.enterList();
    checkGuard(
        size == 3 || size == 4,
        PeerDiscoveryPacketDecodingException::new,
        "Invalid number of components in RLP representation of a peer: expected 3 o 4 but got %s",
        size);

    // Subtract 1 from the total size of the list, to account for the peer ID which will be decoded
    // by us.
    final Endpoint endpoint = Endpoint.decodeInline(in, size - 1);
    final BytesValue id = in.readBytesValue();
    in.leaveList();
    return new DefaultPeer(id, endpoint);
  }

  private static Optional<Integer> extractUdpPortFromQuery(final String query) {
    final Matcher matcher = DISCPORT_QUERY_STRING_REGEX.matcher(query);
    Optional<Integer> answer = Optional.empty();
    if (matcher.matches()) {
      answer = Optional.ofNullable(Ints.tryParse(matcher.group(1)));
    }
    return answer.filter(NetworkUtility::isValidPort);
  }

  /** {@inheritDoc} */
  @Override
  public Endpoint getEndpoint() {
    return endpoint;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof DefaultPeer)) {
      return false;
    }
    final DefaultPeer other = (DefaultPeer) obj;
    return id.equals(other.id) && endpoint.equals(other.endpoint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, endpoint);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("DefaultPeer{");
    sb.append("id=").append(id);
    sb.append(", endpoint=").append(endpoint);
    sb.append('}');
    return sb.toString();
  }
}
