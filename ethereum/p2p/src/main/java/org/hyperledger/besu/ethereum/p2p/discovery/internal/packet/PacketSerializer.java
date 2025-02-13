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
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.vertx.core.buffer.Buffer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

@Singleton
public class PacketSerializer {
  private final PacketSignatureEncoder packetSignatureEncoder;
  private final PingPacketDataRlpWriter pingPacketDataRlpWriter;
  private final PongPacketDataRlpWriter pongPacketDataRlpWriter;
  private final FindNeighborsPacketDataRlpWriter findNeighborsPacketDataRlpWriter;
  private final NeighborsPacketDataRlpWriter neighborsPacketDataRlpWriter;
  private final EnrRequestPacketDataRlpWriter enrRequestPacketDataRlpWriter;
  private final EnrResponsePacketDataRlpWriter enrResponsePacketDataRlpWriter;

  public @Inject PacketSerializer(
      final PacketSignatureEncoder packetSignatureEncoder,
      final PingPacketDataRlpWriter pingPacketDataRlpWriter,
      final PongPacketDataRlpWriter pongPacketDataRlpWriter,
      final FindNeighborsPacketDataRlpWriter findNeighborsPacketDataRlpWriter,
      final NeighborsPacketDataRlpWriter neighborsPacketDataRlpWriter,
      final EnrRequestPacketDataRlpWriter enrRequestPacketDataRlpWriter,
      final EnrResponsePacketDataRlpWriter enrResponsePacketDataRlpWriter) {
    this.packetSignatureEncoder = packetSignatureEncoder;
    this.pingPacketDataRlpWriter = pingPacketDataRlpWriter;
    this.pongPacketDataRlpWriter = pongPacketDataRlpWriter;
    this.findNeighborsPacketDataRlpWriter = findNeighborsPacketDataRlpWriter;
    this.neighborsPacketDataRlpWriter = neighborsPacketDataRlpWriter;
    this.enrRequestPacketDataRlpWriter = enrRequestPacketDataRlpWriter;
    this.enrResponsePacketDataRlpWriter = enrResponsePacketDataRlpWriter;
  }

  public Buffer encode(final Packet packet) {
    final Bytes encodedSignature = packetSignatureEncoder.encodeSignature(packet.getSignature());
    final BytesValueRLPOutput encodedData = new BytesValueRLPOutput();
    switch (packet.getType()) {
      case PING ->
          pingPacketDataRlpWriter.writeTo(
              packet.getPacketData(PingPacketData.class).orElseThrow(), encodedData);
      case PONG ->
          pongPacketDataRlpWriter.writeTo(
              packet.getPacketData(PongPacketData.class).orElseThrow(), encodedData);
      case FIND_NEIGHBORS ->
          findNeighborsPacketDataRlpWriter.writeTo(
              packet.getPacketData(FindNeighborsPacketData.class).orElseThrow(), encodedData);
      case NEIGHBORS ->
          neighborsPacketDataRlpWriter.writeTo(
              packet.getPacketData(NeighborsPacketData.class).orElseThrow(), encodedData);
      case ENR_REQUEST ->
          enrRequestPacketDataRlpWriter.writeTo(
              packet.getPacketData(EnrRequestPacketData.class).orElseThrow(), encodedData);
      case ENR_RESPONSE ->
          enrResponsePacketDataRlpWriter.writeTo(
              packet.getPacketData(EnrResponsePacketData.class).orElseThrow(), encodedData);
    }

    final Buffer buffer =
        Buffer.buffer(
            packet.getHash().size() + encodedSignature.size() + 1 + encodedData.encodedSize());
    packet.getHash().appendTo(buffer);
    encodedSignature.appendTo(buffer);
    buffer.appendByte(packet.getType().getValue());
    appendEncoded(encodedData, buffer);
    return buffer;
  }

  private void appendEncoded(final BytesValueRLPOutput encoded, final Buffer buffer) {
    final int size = encoded.encodedSize();
    if (size == 0) {
      return;
    }

    // We want to append to the buffer, and Buffer always grows to accommodate anything writing,
    // so we write the last byte we know we'll need to make it resize accordingly.
    final int start = buffer.length();
    buffer.setByte(start + size - 1, (byte) 0);
    encoded.writeEncoded(MutableBytes.wrapBuffer(buffer, start, size));
  }
}
