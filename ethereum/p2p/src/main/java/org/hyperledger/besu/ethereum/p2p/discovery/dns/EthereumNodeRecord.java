/*
 * Copyright contributors to Hyperledger Besu.
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

// Adapted from https://github.com/tmio/tuweni and licensed under Apache 2.0
package org.hyperledger.besu.ethereum.p2p.discovery.dns;

import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * A modified implementation of Ethereum Node Record (ENR) that is used by DNSResolver. See <a
 * href="https://eips.ethereum.org/EIPS/eip-778">EIP-778</a>
 */
public record EthereumNodeRecord(
    Bytes rlp, Bytes publicKey, InetAddress ip, Optional<Integer> tcp, Optional<Integer> udp) {

  /**
   * Creates an ENR from its serialized form as a RLP list
   *
   * @param rlp the serialized form of the ENR
   * @return the ENR
   * @throws IllegalArgumentException if the rlp bytes length is longer than 300 bytes
   */
  public static EthereumNodeRecord fromRLP(final Bytes rlp) {
    if (rlp.size() > 300) {
      throw new IllegalArgumentException("Record too long");
    }
    var data = new HashMap<String, Bytes>();

    // rlp: sig, sequence, k1,v1, k2,v2, k3, [v3, vn]...
    var input = new BytesValueRLPInput(rlp, false);
    input.enterList();

    input.skipNext(); // skip signature
    input.skipNext(); // skip sequence

    // go through rest of the list
    while (!input.isEndOfCurrentList()) {
      var key = new String(input.readBytes().toArrayUnsafe(), StandardCharsets.UTF_8);
      if (input.nextIsList()) {
        // skip list as we currently don't need any of these complex structures
        input.skipNext();
      } else {
        data.put(key, input.readBytes());
      }
    }

    input.leaveList();

    var publicKey = initPublicKeyBytes(data);

    return new EthereumNodeRecord(rlp, publicKey, initIPAddr(data), initTCP(data), initUDP(data));
  }

  /**
   * Returns the public key of the ENR
   *
   * @return the public key of the ENR
   */
  static Bytes initPublicKeyBytes(final Map<String, Bytes> data) {
    var keyBytes = data.get("secp256k1");
    if (keyBytes == null) {
      throw new IllegalArgumentException("Missing secp256k1 entry in ENR");
    }
    // convert 33 bytes compressed public key to uncompressed using Bouncy Castle
    var curve = SignatureAlgorithmFactory.getInstance().getCurve();
    var ecPoint = curve.getCurve().decodePoint(keyBytes.toArrayUnsafe());
    // uncompressed public key is 65 bytes, first byte is 0x04.
    var encodedPubKey = ecPoint.getEncoded(false);
    return Bytes.of(Arrays.copyOfRange(encodedPubKey, 1, encodedPubKey.length));
  }

  /**
   * Returns the InetAddress of the ENR
   *
   * @return The IP address of the ENR
   */
  static InetAddress initIPAddr(final Map<String, Bytes> data) {
    var ipBytes = data.get("ip");
    if (ipBytes != null) {
      try {
        return InetAddress.getByAddress(ipBytes.toArrayUnsafe());
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
    return InetAddress.getLoopbackAddress();
  }

  /**
   * The TCP port of the ENR
   *
   * @return the TCP port associated with this ENR
   */
  static Optional<Integer> initTCP(final Map<String, Bytes> data) {
    var tcpBytes = data.get("tcp");
    return tcpBytes != null ? Optional.of(tcpBytes.toInt()) : Optional.empty();
  }

  /**
   * The UDP port of the ENR. If the UDP port is not present, the TCP port is used.
   *
   * @return the UDP port associated with this ENR
   */
  static Optional<Integer> initUDP(final Map<String, Bytes> data) {
    var udpBytes = data.get("udp");
    return udpBytes != null ? Optional.of(udpBytes.toInt()) : initTCP(data);
  }

  /**
   * @return the ENR as a URI
   */
  @Override
  public String toString() {
    return "enr:" + ip() + ":" + tcp() + "?udp=" + udp();
  }

  /** Override equals method to compare the RLP bytes */
  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof EthereumNodeRecord that)) {
      return false;
    }
    return Objects.equals(rlp, that.rlp);
  }

  /** Override hashCode method to use hashCode of the RLP bytes */
  @Override
  public int hashCode() {
    return Objects.hashCode(rlp);
  }
}
