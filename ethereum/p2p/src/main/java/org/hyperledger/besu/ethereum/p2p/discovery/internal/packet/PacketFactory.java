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

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryPacketDecodingException;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PacketType;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.enrrequest.EnrRequestPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.enrrequest.EnrRequestPacketDataRlpWriter;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.enrresponse.EnrResponsePacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.enrresponse.EnrResponsePacketDataRlpWriter;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.findneighbors.FindNeighborsPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.findneighbors.FindNeighborsPacketDataRlpWriter;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.neighbors.NeighborsPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.neighbors.NeighborsPacketDataRlpWriter;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.ping.PingPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.ping.PingPacketDataRlpWriter;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.pong.PongPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.pong.PongPacketDataRlpWriter;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.math.BigInteger;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.base.Preconditions;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

@Singleton
public class PacketFactory {
  private final PingPacketDataRlpWriter pingPacketDataRlpWriter;
  private final PongPacketDataRlpWriter pongPacketDataRlpWriter;
  private final FindNeighborsPacketDataRlpWriter findNeighborsPacketDataRlpWriter;
  private final NeighborsPacketDataRlpWriter neighborsPacketDataRlpWriter;
  private final EnrRequestPacketDataRlpWriter enrRequestPacketDataRlpWriter;
  private final EnrResponsePacketDataRlpWriter enrResponsePacketDataRlpWriter;
  private final SignatureAlgorithm signatureAlgorithm;
  private final PacketSignatureEncoder packetSignatureEncoder;

  public @Inject PacketFactory(
      final PingPacketDataRlpWriter pingPacketDataRlpWriter,
      final PongPacketDataRlpWriter pongPacketDataRlpWriter,
      final FindNeighborsPacketDataRlpWriter findNeighborsPacketDataRlpWriter,
      final NeighborsPacketDataRlpWriter neighborsPacketDataRlpWriter,
      final EnrRequestPacketDataRlpWriter enrRequestPacketDataRlpWriter,
      final EnrResponsePacketDataRlpWriter enrResponsePacketDataRlpWriter,
      final SignatureAlgorithm signatureAlgorithm,
      final PacketSignatureEncoder packetSignatureEncoder) {
    this.pingPacketDataRlpWriter = pingPacketDataRlpWriter;
    this.pongPacketDataRlpWriter = pongPacketDataRlpWriter;
    this.findNeighborsPacketDataRlpWriter = findNeighborsPacketDataRlpWriter;
    this.neighborsPacketDataRlpWriter = neighborsPacketDataRlpWriter;
    this.enrRequestPacketDataRlpWriter = enrRequestPacketDataRlpWriter;
    this.enrResponsePacketDataRlpWriter = enrResponsePacketDataRlpWriter;
    this.signatureAlgorithm = signatureAlgorithm;
    this.packetSignatureEncoder = packetSignatureEncoder;
  }

  public Packet create(final PacketType type, final PacketData data, final NodeKey nodeKey) {
    final Bytes typeBytes = Bytes.of(type.getValue());
    final Bytes dataBytes =
        RLP.encode(
            (rlpOutput) -> {
              switch (type) {
                case PING -> pingPacketDataRlpWriter.writeTo((PingPacketData) data, rlpOutput);
                case PONG -> pongPacketDataRlpWriter.writeTo((PongPacketData) data, rlpOutput);
                case FIND_NEIGHBORS ->
                    findNeighborsPacketDataRlpWriter.writeTo(
                        (FindNeighborsPacketData) data, rlpOutput);
                case NEIGHBORS ->
                    neighborsPacketDataRlpWriter.writeTo((NeighborsPacketData) data, rlpOutput);
                case ENR_REQUEST ->
                    enrRequestPacketDataRlpWriter.writeTo((EnrRequestPacketData) data, rlpOutput);
                case ENR_RESPONSE ->
                    enrResponsePacketDataRlpWriter.writeTo((EnrResponsePacketData) data, rlpOutput);
              }
            });

    SECPSignature signature = nodeKey.sign(Hash.keccak256(Bytes.wrap(typeBytes, dataBytes)));
    Bytes32 hash =
        Hash.keccak256(
            Bytes.concatenate(
                packetSignatureEncoder.encodeSignature(signature), typeBytes, dataBytes));
    SECPPublicKey publicKey = nodeKey.getPublicKey();

    return new Packet(type, data, hash, signature, publicKey);
  }

  public Packet create(
      final PacketType packetType, final PacketData packetData, final Bytes message) {
    final Bytes hash = message.slice(0, Packet.SIGNATURE_INDEX);
    final Bytes encodedSignature =
        message.slice(Packet.SIGNATURE_INDEX, Packet.PACKET_TYPE_INDEX - Packet.SIGNATURE_INDEX);
    final Bytes signedPayload =
        message.slice(Packet.PACKET_TYPE_INDEX, message.size() - Packet.PACKET_TYPE_INDEX);

    // Perform hash integrity check.
    final Bytes rest =
        message.slice(Packet.SIGNATURE_INDEX, message.size() - Packet.SIGNATURE_INDEX);
    if (!Arrays.equals(Hash.keccak256(rest).toArray(), hash.toArray())) {
      throw new PeerDiscoveryPacketDecodingException(
          "Integrity check failed: non-matching hashes.");
    }

    SECPSignature signature = decodeSignature(encodedSignature);
    SECPPublicKey publicKey =
        signatureAlgorithm
            .recoverPublicKeyFromSignature(Hash.keccak256(signedPayload), signature)
            .orElseThrow(
                () ->
                    new PeerDiscoveryPacketDecodingException(
                        "Invalid packet signature, cannot recover public key"));
    return new Packet(packetType, packetData, hash, signature, publicKey);
  }

  private SECPSignature decodeSignature(final Bytes encodedSignature) {
    Preconditions.checkArgument(
        encodedSignature != null && encodedSignature.size() == 65,
        "encodedSignature is not 65 bytes");
    final BigInteger r = encodedSignature.slice(0, 32).toUnsignedBigInteger();
    final BigInteger s = encodedSignature.slice(32, 32).toUnsignedBigInteger();
    final int recId = encodedSignature.get(64);
    return signatureAlgorithm.createSignature(r, s, (byte) recId);
  }
}
