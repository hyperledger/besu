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
package tech.pegasys.pantheon.ethereum.p2p.wire;

import static tech.pegasys.pantheon.util.bytes.BytesValue.wrap;

import tech.pegasys.pantheon.crypto.SECP256K1.PublicKey;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates information about a peer, including their protocol version, client ID, capabilities
 * and other.
 *
 * <p>The peer info is shared between peers during the <code>HELLO</code> wire protocol handshake.
 */
public class PeerInfo {
  private final int version;
  private final String clientId;
  private final List<Capability> capabilities;
  private final int port;
  private final BytesValue nodeId;

  public PeerInfo(
      final int version,
      final String clientId,
      final List<Capability> capabilities,
      final int port,
      final BytesValue nodeId) {
    this.version = version;
    this.clientId = clientId;
    this.capabilities = capabilities;
    this.port = port;
    this.nodeId = nodeId;
  }

  public static PeerInfo readFrom(final RLPInput in) {
    in.enterList();
    final int version = in.readUnsignedByte();
    final String clientId = in.readBytesValue(BytesValues::asString);
    final List<Capability> caps =
        in.nextIsNull() ? Collections.emptyList() : in.readList(Capability::readFrom);
    final int port = in.readIntScalar();
    final BytesValue nodeId = in.readBytesValue();
    in.leaveList(true);
    return new PeerInfo(version, clientId, caps, port, nodeId);
  }

  public int getVersion() {
    return version;
  }

  public String getClientId() {
    return clientId;
  }

  public List<Capability> getCapabilities() {
    return capabilities;
  }

  /**
   * This value is meant to represent the port at which a peer is listening for connections.
   * However, most peers actually advertise a port of "0" so this value is not reliable.
   *
   * @return (Unreliable) The tcp port on which the peer is listening for connections
   */
  public int getPort() {
    return port;
  }

  public BytesValue getNodeId() {
    return nodeId;
  }

  public Address getAddress() {
    final PublicKey remotePublicKey = PublicKey.create(nodeId);
    return Util.publicKeyToAddress(remotePublicKey);
  }

  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeUnsignedByte(getVersion());
    out.writeBytesValue(wrap(getClientId().getBytes(StandardCharsets.UTF_8)));
    out.writeList(getCapabilities(), Capability::writeTo);
    out.writeIntScalar(getPort());
    out.writeBytesValue(getNodeId());
    out.endList();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("PeerInfo{");
    sb.append("version=").append(version);
    sb.append(", clientId='").append(clientId).append('\'');
    sb.append(", capabilities=").append(capabilities);
    sb.append(", port=").append(port);
    sb.append(", nodeId=").append(nodeId);
    sb.append('}');
    return sb.toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PeerInfo)) {
      return false;
    }
    final PeerInfo peerInfo = (PeerInfo) o;
    return version == peerInfo.version
        && port == peerInfo.port
        && Objects.equals(clientId, peerInfo.clientId)
        && Objects.equals(capabilities, peerInfo.capabilities)
        && Objects.equals(nodeId, peerInfo.nodeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, clientId, capabilities, port, nodeId);
  }
}
