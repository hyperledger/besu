/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static org.hyperledger.besu.crypto.Hash.keccak256;
import static org.hyperledger.besu.util.Preconditions.checkGuard;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryPacketDecodingException;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import io.vertx.core.buffer.Buffer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;

public class Packet {

  private static final int PACKET_TYPE_INDEX = 97;
  private static final int PACKET_DATA_INDEX = 98;
  private static final int SIGNATURE_INDEX = 32;
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private final PacketType type;
  private final PacketData data;

  private final Bytes hash;
  private final SECPSignature signature;
  private final SECPPublicKey publicKey;

  private Packet(final PacketType type, final PacketData data, final NodeKey nodeKey) {
    this.type = type;
    this.data = data;

    final Bytes typeBytes = Bytes.of(this.type.getValue());
    final Bytes dataBytes = RLP.encode(this.data::writeTo);

    this.signature = nodeKey.sign(keccak256(Bytes.wrap(typeBytes, dataBytes)));
    this.hash = keccak256(Bytes.concatenate(encodeSignature(signature), typeBytes, dataBytes));
    this.publicKey = nodeKey.getPublicKey();
  }

  private Packet(final PacketType packetType, final PacketData packetData, final Bytes message) {
    final Bytes hash = message.slice(0, SIGNATURE_INDEX);
    final Bytes encodedSignature =
        message.slice(SIGNATURE_INDEX, PACKET_TYPE_INDEX - SIGNATURE_INDEX);
    final Bytes signedPayload =
        message.slice(PACKET_TYPE_INDEX, message.size() - PACKET_TYPE_INDEX);

    // Perform hash integrity check.
    final Bytes rest = message.slice(SIGNATURE_INDEX, message.size() - SIGNATURE_INDEX);
    if (!Arrays.equals(keccak256(rest).toArray(), hash.toArray())) {
      throw new PeerDiscoveryPacketDecodingException(
          "Integrity check failed: non-matching hashes.");
    }

    this.type = packetType;
    this.data = packetData;
    this.hash = hash;
    this.signature = decodeSignature(encodedSignature);
    this.publicKey =
        SIGNATURE_ALGORITHM
            .get()
            .recoverPublicKeyFromSignature(keccak256(signedPayload), this.signature)
            .orElseThrow(
                () ->
                    new PeerDiscoveryPacketDecodingException(
                        "Invalid packet signature, " + "cannot recover public key"));
  }

  public static Packet create(
      final PacketType packetType, final PacketData packetData, final NodeKey nodeKey) {
    return new Packet(packetType, packetData, nodeKey);
  }

  public static Packet decode(final Buffer message) {
    checkGuard(
        message.length() >= PACKET_DATA_INDEX,
        PeerDiscoveryPacketDecodingException::new,
        "Packet too short: expected at least %s bytes, got %s",
        PACKET_DATA_INDEX,
        message.length());

    final byte type = message.getByte(PACKET_TYPE_INDEX);

    final PacketType packetType =
        PacketType.forByte(type)
            .orElseThrow(
                () ->
                    new PeerDiscoveryPacketDecodingException("Unrecognized packet type: " + type));

    final PacketType.Deserializer<?> deserializer = packetType.getDeserializer();
    final PacketData packetData;
    try {
      packetData =
          deserializer.deserialize(
              RLP.input(
                  Bytes.wrapBuffer(
                      message, PACKET_DATA_INDEX, message.length() - PACKET_DATA_INDEX)));
      return new Packet(packetType, packetData, Bytes.wrapBuffer(message));
    } catch (final RLPException e) {
      throw new PeerDiscoveryPacketDecodingException("Malformed packet of type: " + packetType, e);
    } catch (final IllegalArgumentException e) {
      throw new PeerDiscoveryPacketDecodingException(
          "Failed decoding packet of type: " + packetType, e);
    }
  }

  public Buffer encode() {
    final Bytes encodedSignature = encodeSignature(signature);
    final BytesValueRLPOutput encodedData = new BytesValueRLPOutput();
    data.writeTo(encodedData);

    final Buffer buffer =
        Buffer.buffer(hash.size() + encodedSignature.size() + 1 + encodedData.encodedSize());
    hash.appendTo(buffer);
    encodedSignature.appendTo(buffer);
    buffer.appendByte(type.getValue());
    appendEncoded(encodedData, buffer);
    return buffer;
  }

  protected void appendEncoded(final BytesValueRLPOutput encoded, final Buffer buffer) {
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

  private static Bytes encodeSignature(final SECPSignature signature) {
    final MutableBytes encoded = MutableBytes.create(65);
    UInt256.valueOf(signature.getR()).copyTo(encoded, 0);
    UInt256.valueOf(signature.getS()).copyTo(encoded, 32);
    final int v = signature.getRecId();
    encoded.set(64, (byte) v);
    return encoded;
  }

  private static SECPSignature decodeSignature(final Bytes encodedSignature) {
    checkArgument(
        encodedSignature != null && encodedSignature.size() == 65, "encodedSignature is 65 bytes");
    final BigInteger r = encodedSignature.slice(0, 32).toUnsignedBigInteger();
    final BigInteger s = encodedSignature.slice(32, 32).toUnsignedBigInteger();
    final int recId = encodedSignature.get(64);
    return SIGNATURE_ALGORITHM.get().createSignature(r, s, (byte) recId);
  }
}
