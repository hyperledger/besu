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

import tech.pegasys.pantheon.crypto.SecureRandomProvider;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.OptionalInt;

import org.bouncycastle.util.encoders.Hex;

public interface Peer extends PeerId {

  /**
   * A struct-like immutable object encapsulating the peer's network coordinates, namely their
   * hostname (as an IP address in the current implementation), UDP port and optional TCP port for
   * RLPx communications.
   *
   * @return An object encapsulating the peer's network coordinates.
   */
  Endpoint getEndpoint();

  /**
   * Generates a random peer ID in a secure manner.
   *
   * @return The generated peer ID.
   */
  static BytesValue randomId() {
    final byte[] id = new byte[64];
    SecureRandomProvider.publicSecureRandom().nextBytes(id);
    return BytesValue.wrap(id);
  }

  /**
   * Encodes this peer to its RLP representation.
   *
   * @param out The RLP output stream to which to write.
   */
  default void writeTo(final RLPOutput out) {
    out.startList();
    getEndpoint().encodeInline(out);
    out.writeBytesValue(getId());
    out.endList();
  }

  /**
   * Returns this peer's enode URI.
   *
   * @return The enode URI as a String.
   */
  default String getEnodeURI() {
    String url = Hex.toHexString(this.getId().extractArray());
    Endpoint endpoint = this.getEndpoint();
    String nodeIp = endpoint.getHost();
    OptionalInt tcpPort = endpoint.getTcpPort();
    int udpPort = endpoint.getUdpPort();

    if (tcpPort.isPresent() && (tcpPort.getAsInt() != udpPort)) {
      return String.format(
          "enode://%s@%s:%d?discport=%d", url, nodeIp, tcpPort.getAsInt(), udpPort);
    } else {
      return String.format("enode://%s@%s:%d", url, nodeIp, udpPort);
    }
  }
}
