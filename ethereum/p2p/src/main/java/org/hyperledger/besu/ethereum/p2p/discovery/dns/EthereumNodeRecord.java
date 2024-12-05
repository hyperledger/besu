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

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.rlp.RLP;
import org.apache.tuweni.rlp.RLPReader;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.math.ec.ECPoint;

/** A modified implementation of Ethereum Node Record (ENR) that is used by DNSResolver.*/
public class EthereumNodeRecord {
  private final Map<String, Bytes> data;
  private final Bytes rlp;

  /**
   * Creates an ENR from its components
   *
   * @param signature the record signature
   * @param seq the sequence of the record, its revision number
   * @param data the arbitrary data of the record
   * @param listData the arbitrary data of the record as list
   * @param rlp RLP encoding of the record
   */
  public EthereumNodeRecord(
      final Bytes signature,
      final long seq,
      final Map<String, Bytes> data,
      final Map<String, List<Bytes>> listData,
      final Bytes rlp) {
    this.data = data;
    this.rlp = rlp;
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
    // TODO: Use Besu classes
    return RLP.decodeList(rlp, reader -> fromRLP(reader, rlp));
  }

  /**
   * Creates an ENR from its serialized form as a RLP list
   *
   * @param reader the RLP reader
   * @param rlp the serialized form of the ENR
   * @return the ENR
   * @throws IllegalArgumentException if the rlp bytes length is longer than 300 bytes
   */
  public static EthereumNodeRecord fromRLP(final RLPReader reader, final Bytes rlp) {
    var sig = reader.readValue();
    var seq = reader.readLong();

    var data = new HashMap<String, Bytes>();
    var listData = new HashMap<String, List<Bytes>>();
    while (!reader.isComplete()) {
      var key = reader.readString();
      if (reader.nextIsList()) {
        listData.put(
            key,
            reader
                .readListContents(
                    listReader -> {
                      if (listReader.nextIsList()) {
                        // TODO complex structures not supported
                        listReader.skipNext();
                        return null;
                      } else {
                        return listReader.readValue();
                      }
                    })
                .stream()
                .filter(Objects::nonNull)
                .toList());
      } else {
        var value = reader.readValue();
        data.put(key, value);
      }
    }

    return new EthereumNodeRecord(sig, seq, data, listData, rlp);
  }

  /**
   * Encoded public key/nodeid of the ENR
   *
   * @return the public key of the ENR
   */
  public Bytes publicKey() {
    var keyBytes = data.get("secp256k1");
    if (keyBytes == null) {
      throw new IllegalArgumentException("Missing secp256k1 entry in ENR");
    }
    // 33 bytes compressed format public key
    final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    final ECDomainParameters curve = signatureAlgorithm.getCurve();
    final ECPoint ecPoint = curve.getCurve().decodePoint(keyBytes.toArrayUnsafe());
    // convert to 64 byte uncompressed format public key (slicing the prefix)
    return Bytes.wrap(ecPoint.getEncoded(false)).slice(1);
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
