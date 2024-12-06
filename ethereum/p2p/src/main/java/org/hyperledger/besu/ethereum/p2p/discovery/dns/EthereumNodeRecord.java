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
import java.util.HashMap;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;

/**
 * A modified implementation of Ethereum Node Record (ENR) that is used by DNSResolver. See <a
 * href="https://eips.ethereum.org/EIPS/eip-778">EIP-778</a>
 */
public class EthereumNodeRecord {
  private final Map<String, Bytes> data;
  private final Bytes rlp;
  private final Bytes publicKey;

  /**
   * Creates an ENR from its components
   *
   * @param data the arbitrary data of the record
   * @param rlp RLP encoding of the record
   */
  public EthereumNodeRecord(final Map<String, Bytes> data, final Bytes rlp) {
    this.data = data;
    this.rlp = rlp;
    publicKey = initPublicKeyBytes(data);
  }

  private static Bytes initPublicKeyBytes(final Map<String, Bytes> data) {
    var keyBytes = data.get("secp256k1");
    if (keyBytes == null) {
      throw new IllegalArgumentException("Missing secp256k1 entry in ENR");
    }
    // convert 33 bytes compressed public key to uncompressed using Bouncy Castle
    var curve = SignatureAlgorithmFactory.getInstance().getCurve();
    var ecPoint = curve.getCurve().decodePoint(keyBytes.toArrayUnsafe());
    return Bytes.wrap(ecPoint.getEncoded(false)).slice(1);
  }

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
    return new EthereumNodeRecord(data, rlp);
  }

  /**
   * Decompressed 64 bytes public key of the ENR
   *
   * @return the public key of the ENR
   */
  public Bytes publicKey() {
    return publicKey;
  }

  /**
   * The ip associated with the ENR
   *
   * @return The IP address of the ENR
   */
  public InetAddress ip() {
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
  public Integer tcp() {
    var tcpBytes = data.get("tcp");
    return tcpBytes != null ? tcpBytes.toInt() : null;
  }

  /**
   * The UDP port of the ENR
   *
   * @return the UDP port associated with this ENR
   */
  public Integer udp() {
    var udpBytes = data.get("udp");
    return udpBytes != null ? udpBytes.toInt() : tcp();
  }

  /**
   * @return the ENR as a URI
   */
  @Override
  public String toString() {
    return "enr:" + ip() + ":" + tcp() + "?udp=" + udp();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EthereumNodeRecord that = (EthereumNodeRecord) o;
    return rlp.equals(that.rlp);
  }

  @Override
  public int hashCode() {
    return rlp.hashCode();
  }
}
