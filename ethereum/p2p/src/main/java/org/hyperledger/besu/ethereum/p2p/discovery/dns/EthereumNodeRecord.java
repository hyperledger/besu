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

import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;

/**
 * A modified implementation of Ethereum Node Record (ENR) that is used by DNSResolver. See <a
 * href="https://eips.ethereum.org/EIPS/eip-778">EIP-778</a>
 */
public record EthereumNodeRecord(
    Bytes publicKey,
    InetAddress ip,
    Optional<Integer> tcp,
    Optional<Integer> udp,
    NodeRecord nodeRecord) {

  /**
   * Creates an EthereumNodeRecord from an ENR string
   *
   * @param enr the ENR string
   * @return the EthereumNodeRecord
   */
  public static EthereumNodeRecord fromEnr(final String enr) {
    NodeRecord nodeRecord = NodeRecordFactory.DEFAULT.fromEnr(enr);
    final Map<String, Object> fields = new HashMap<>();
    nodeRecord.forEachField(fields::put);
    return new EthereumNodeRecord(
        initPublicKeyBytes(fields),
        initIPAddr(fields),
        initTCP(fields),
        initUDP(fields),
        nodeRecord);
  }

  /**
   * Returns the public key of the ENR
   *
   * @return the public key of the ENR
   */
  static Bytes initPublicKeyBytes(final Map<String, Object> fields) {
    final Object value = fields.get("secp256k1");
    if (!(value instanceof Bytes keyBytes)) {
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
  static InetAddress initIPAddr(final Map<String, Object> fields) {
    final Object value = fields.get("ip");
    if (value instanceof Bytes ipBytes) {
      try {
        return InetAddress.getByAddress(ipBytes.toArrayUnsafe());
      } catch (final Exception e) {
        throw new RuntimeException("Invalid IP address in ENR", e);
      }
    }
    return InetAddress.getLoopbackAddress();
  }

  /**
   * The TCP port of the ENR
   *
   * @return the TCP port associated with this ENR
   */
  static Optional<Integer> initTCP(final Map<String, Object> fields) {
    return extractInt(fields, "tcp");
  }

  /**
   * The UDP port of the ENR. If the UDP port is not present, the TCP port is used.
   *
   * @return the UDP port associated with this ENR
   */
  static Optional<Integer> initUDP(final Map<String, Object> fields) {
    return extractInt(fields, "udp").or(() -> initTCP(fields));
  }

  private static Optional<Integer> extractInt(final Map<String, Object> fields, final String key) {
    final Object value = fields.get(key);
    if (value instanceof Integer integer) {
      return Optional.of(integer);
    }
    return Optional.empty();
  }

  /**
   * @return the ENR as a URI
   */
  @Override
  public String toString() {
    return "enr:" + ip() + ":" + tcp() + "?udp=" + udp();
  }

  @Override
  public int hashCode() {
    return Objects.hash(publicKey, ip, tcp, udp, nodeRecord);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final EthereumNodeRecord other = (EthereumNodeRecord) obj;
    return Objects.equals(this.publicKey, other.publicKey)
        && Objects.equals(this.ip, other.ip)
        && Objects.equals(this.tcp, other.tcp)
        && Objects.equals(this.udp, other.udp)
        && Objects.equals(this.nodeRecord, other.nodeRecord);
  }
}
