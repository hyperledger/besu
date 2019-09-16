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
package org.hyperledger.besu.ethereum.p2p.discovery;

import static com.google.common.base.Preconditions.checkArgument;
import static org.hyperledger.besu.util.Preconditions.checkGuard;

import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.NetworkUtility;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.net.InetAddress;
import java.util.Objects;
import java.util.OptionalInt;

import com.google.common.net.InetAddresses;

/**
 * Encapsulates the network coordinates of a {@link DiscoveryPeer} as well as serialization logic
 * used in various Discovery messages.
 */
public class Endpoint {
  private final String host;
  private final int udpPort;
  private final OptionalInt tcpPort;

  public Endpoint(final String host, final int udpPort, final OptionalInt tcpPort) {
    checkArgument(
        host != null && InetAddresses.isInetAddress(host), "host requires a valid IP address");
    checkArgument(
        NetworkUtility.isValidPort(udpPort), "UDP port requires a value between 1 and 65535");
    tcpPort.ifPresent(
        p ->
            checkArgument(
                NetworkUtility.isValidPort(p), "TCP port requires a value between 1 and 65535"));

    this.host = host;
    this.udpPort = udpPort;
    this.tcpPort = tcpPort;
  }

  public static Endpoint fromEnode(final EnodeURL enode) {
    checkArgument(
        enode.getDiscoveryPort().isPresent(),
        "Attempt to create a discovery endpoint for an enode with discovery disabled.");
    final int discoveryPort = enode.getDiscoveryPort().getAsInt();
    final OptionalInt listeningPort = enode.getListeningPort();
    return new Endpoint(enode.getIp().getHostAddress(), discoveryPort, listeningPort);
  }

  public EnodeURL toEnode(final BytesValue nodeId) {
    return EnodeURL.builder()
        .nodeId(nodeId)
        .ipAddress(host)
        .listeningPort(tcpPort.orElse(udpPort))
        .discoveryPort(udpPort)
        .build();
  }

  public String getHost() {
    return host;
  }

  public int getUdpPort() {
    return udpPort;
  }

  public OptionalInt getTcpPort() {
    return tcpPort;
  }

  /**
   * If the tcp port is explicitly defined, return it. Otherwise, return the udp port assuming that
   * the tcp port should match the udp port.
   *
   * @return The tcp port to use for this endpoint.
   */
  public int getFunctionalTcpPort() {
    return tcpPort.orElse(udpPort);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof Endpoint)) {
      return false;
    }
    final Endpoint other = (Endpoint) obj;
    return host.equals(other.host)
        && this.udpPort == other.udpPort
        && (this.tcpPort.equals(other.tcpPort));
  }

  @Override
  public int hashCode() {
    return Objects.hash(host, udpPort, tcpPort);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("Endpoint{");
    sb.append("host='").append(host).append('\'');
    sb.append(", udpPort=").append(udpPort);
    tcpPort.ifPresent(p -> sb.append(", getTcpPort=").append(p));
    sb.append('}');
    return sb.toString();
  }

  /**
   * Encodes this endpoint into a standalone object.
   *
   * @param out The RLP output stream.
   */
  public void encodeStandalone(final RLPOutput out) {
    out.startList();
    encodeInline(out);
    out.endList();
  }

  /**
   * Encodes this endpoint to an RLP representation that is inlined into a containing object
   * (generally a Peer).
   *
   * @param out The RLP output stream.
   */
  public void encodeInline(final RLPOutput out) {
    out.writeInetAddress(InetAddresses.forString(host));
    out.writeUnsignedShort(udpPort);
    if (tcpPort.isPresent()) {
      out.writeUnsignedShort(tcpPort.getAsInt());
    } else {
      out.writeNull();
    }
  }

  /**
   * Decodes the input stream as an Endpoint instance appearing inline within another object
   * (generally a Peer).
   *
   * @param fieldCount The number of fields RLP list.
   * @param in The RLP input stream from which to read.
   * @return The decoded endpoint.
   */
  public static Endpoint decodeInline(final RLPInput in, final int fieldCount) {
    checkGuard(
        fieldCount == 2 || fieldCount == 3,
        PeerDiscoveryPacketDecodingException::new,
        "Invalid number of components in RLP representation of an endpoint: expected 2 o 3 elements but got %s",
        fieldCount);

    final InetAddress addr = in.readInetAddress();
    final int udpPort = in.readUnsignedShort();

    // Some mainnet packets have been shown to either not have the TCP port field at all,
    // or to have an RLP NULL value for it.
    OptionalInt tcpPort = OptionalInt.empty();
    if (fieldCount == 3) {
      if (in.nextIsNull()) {
        in.skipNext();
      } else {
        tcpPort = OptionalInt.of(in.readUnsignedShort());
      }
    }
    return new Endpoint(addr.getHostAddress(), udpPort, tcpPort);
  }

  /**
   * Decodes the RLP stream as a standalone Endpoint instance, which is not part of a Peer.
   *
   * @param in The RLP input stream from which to read.
   * @return The decoded endpoint.
   */
  public static Endpoint decodeStandalone(final RLPInput in) {
    final int size = in.enterList();
    final Endpoint endpoint = decodeInline(in, size);
    in.leaveList();
    return endpoint;
  }
}
