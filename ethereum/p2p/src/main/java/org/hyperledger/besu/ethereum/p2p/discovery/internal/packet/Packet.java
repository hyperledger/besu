/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet;

import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PacketType;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class Packet {
  public static final int PACKET_TYPE_INDEX = 97;
  public static final int PACKET_DATA_INDEX = 98;
  public static final int SIGNATURE_INDEX = 32;

  private final PacketType type;
  private final PacketData data;
  private final Bytes hash;
  private final SECPSignature signature;
  private final SECPPublicKey publicKey;

  Packet(
      final PacketType type,
      final PacketData data,
      final Bytes hash,
      final SECPSignature signature,
      final SECPPublicKey publicKey) {
    this.type = type;
    this.data = data;
    this.hash = hash;
    this.signature = signature;
    this.publicKey = publicKey;
  }

  public PacketData getPacketData() {
    return data;
  }

  @SuppressWarnings("unchecked")
  public <T extends PacketData> Optional<T> getPacketData(final Class<T> expectedPacketType) {
    if (data == null || !data.getClass().equals(expectedPacketType)) {
      return Optional.empty();
    }
    return Optional.of((T) data);
  }

  public Bytes getNodeId() {
    return publicKey.getEncodedBytes();
  }

  public PacketType getType() {
    return type;
  }

  public Bytes getHash() {
    return hash;
  }

  public SECPSignature getSignature() {
    return signature;
  }

  @Override
  public String toString() {
    return "Packet{"
        + "type="
        + type
        + ", data="
        + data
        + ", hash="
        + hash
        + ", signature="
        + signature
        + ", publicKey="
        + publicKey
        + '}';
  }
}
