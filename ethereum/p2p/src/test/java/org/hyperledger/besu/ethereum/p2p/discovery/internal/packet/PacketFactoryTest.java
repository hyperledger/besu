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
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.lang.reflect.Field;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PacketFactoryTest {
  private @Mock PingPacketDataRlpWriter pingPacketDataRlpWriter;
  private @Mock PongPacketDataRlpWriter pongPacketDataRlpWriter;
  private @Mock FindNeighborsPacketDataRlpWriter findNeighborsPacketDataRlpWriter;
  private @Mock NeighborsPacketDataRlpWriter neighborsPacketDataRlpWriter;
  private @Mock EnrRequestPacketDataRlpWriter enrRequestPacketDataRlpWriter;
  private @Mock EnrResponsePacketDataRlpWriter enrResponsePacketDataRlpWriter;
  private @Mock SignatureAlgorithm signatureAlgorithm;
  private @Mock PacketSignatureEncoder packetSignatureEncoder;

  private PacketFactory factory;

  @BeforeEach
  public void beforeTest() {
    factory =
        new PacketFactory(
            pingPacketDataRlpWriter,
            pongPacketDataRlpWriter,
            findNeighborsPacketDataRlpWriter,
            neighborsPacketDataRlpWriter,
            enrRequestPacketDataRlpWriter,
            enrResponsePacketDataRlpWriter,
            signatureAlgorithm,
            packetSignatureEncoder);
  }

  @Test
  public void testCreatePingPacketWithNodeKey() throws IllegalAccessException {
    final PingPacketData packetData = Mockito.mock(PingPacketData.class);
    final NodeKey nodeKey = Mockito.mock(NodeKey.class);
    final SECPSignature secpSignature = Mockito.mock(SECPSignature.class);
    final SECPPublicKey secpPublicKey = Mockito.mock(SECPPublicKey.class);

    Mockito.doAnswer(
            (invocationOnMock) -> {
              RLPOutput out = invocationOnMock.getArgument(1, RLPOutput.class);
              out.startList();
              out.writeBytes(Bytes.repeat((byte) 0xbb, 32));
              out.endList();
              return null;
            })
        .when(pingPacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));

    Mockito.when(nodeKey.sign(Mockito.any(Bytes32.class))).thenReturn(secpSignature);
    Mockito.when(packetSignatureEncoder.encodeSignature(secpSignature))
        .thenReturn(Bytes.fromHexString("0xeeeeeeeeeeeeeeee"));
    Mockito.when(nodeKey.getPublicKey()).thenReturn(secpPublicKey);

    Packet packet = factory.create(PacketType.PING, packetData, nodeKey);

    Mockito.verify(pingPacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));
    ArgumentCaptor<Bytes32> typeAndDataBytesHashCaptor = ArgumentCaptor.forClass(Bytes32.class);
    Mockito.verify(nodeKey).sign(typeAndDataBytesHashCaptor.capture());
    Mockito.verify(packetSignatureEncoder).encodeSignature(secpSignature);
    Mockito.verify(nodeKey).getPublicKey();
    Mockito.verifyNoInteractions(
        pongPacketDataRlpWriter,
        findNeighborsPacketDataRlpWriter,
        neighborsPacketDataRlpWriter,
        enrRequestPacketDataRlpWriter,
        enrResponsePacketDataRlpWriter,
        signatureAlgorithm);

    Assertions.assertEquals(
        "0x1d82b79365133086f85611e56d5cf9bf91c64d901f38e415735c7b6ec3cbb3fe",
        typeAndDataBytesHashCaptor.getValue().toHexString());

    Assertions.assertNotNull(packet);
    Assertions.assertEquals(PacketType.PING, packet.getType());
    Assertions.assertEquals(packetData, packet.getPacketData());
    Assertions.assertEquals(
        "0x771d602d8630675967b911a12988f07ec2697902268ad412f9e1842ee32be1a6",
        packet.getHash().toHexString());
    Assertions.assertEquals(secpSignature, packet.getSignature());
    Field publicKeyField =
        ReflectionUtils.findFields(
                Packet.class,
                (f) -> f.getName().equals("publicKey"),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
            .getFirst();
    publicKeyField.setAccessible(true);
    Assertions.assertEquals(secpPublicKey, publicKeyField.get(packet));
    publicKeyField.setAccessible(false);
  }

  @Test
  public void testCreatePongPacketWithNodeKey() throws IllegalAccessException {
    final PongPacketData packetData = Mockito.mock(PongPacketData.class);
    final NodeKey nodeKey = Mockito.mock(NodeKey.class);
    final SECPSignature secpSignature = Mockito.mock(SECPSignature.class);
    final SECPPublicKey secpPublicKey = Mockito.mock(SECPPublicKey.class);

    Mockito.doAnswer(
            (invocationOnMock) -> {
              RLPOutput out = invocationOnMock.getArgument(1, RLPOutput.class);
              out.startList();
              out.writeBytes(Bytes.repeat((byte) 0xbb, 32));
              out.endList();
              return null;
            })
        .when(pongPacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));

    Mockito.when(nodeKey.sign(Mockito.any(Bytes32.class))).thenReturn(secpSignature);
    Mockito.when(packetSignatureEncoder.encodeSignature(secpSignature))
        .thenReturn(Bytes.fromHexString("0xeeeeeeeeeeeeeeee"));
    Mockito.when(nodeKey.getPublicKey()).thenReturn(secpPublicKey);

    Packet packet = factory.create(PacketType.PONG, packetData, nodeKey);

    Mockito.verify(pongPacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));
    ArgumentCaptor<Bytes32> typeAndDataBytesHashCaptor = ArgumentCaptor.forClass(Bytes32.class);
    Mockito.verify(nodeKey).sign(typeAndDataBytesHashCaptor.capture());
    Mockito.verify(packetSignatureEncoder).encodeSignature(secpSignature);
    Mockito.verify(nodeKey).getPublicKey();
    Mockito.verifyNoInteractions(
        pingPacketDataRlpWriter,
        findNeighborsPacketDataRlpWriter,
        neighborsPacketDataRlpWriter,
        enrRequestPacketDataRlpWriter,
        enrResponsePacketDataRlpWriter,
        signatureAlgorithm);

    Assertions.assertEquals(
        "0xbb86992fd5aeeac25ccfbaf86830fa7ab8987d9668e33f7ef4355a6b0da3830c",
        typeAndDataBytesHashCaptor.getValue().toHexString());

    Assertions.assertNotNull(packet);
    Assertions.assertEquals(PacketType.PONG, packet.getType());
    Assertions.assertEquals(packetData, packet.getPacketData());
    Assertions.assertEquals(
        "0xbcafc42be6d5b78eac26e6c1a596b31606eee23824ab8a771d5ec0fbde869c8f",
        packet.getHash().toHexString());
    Assertions.assertEquals(secpSignature, packet.getSignature());
    Field publicKeyField =
        ReflectionUtils.findFields(
                Packet.class,
                (f) -> f.getName().equals("publicKey"),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
            .getFirst();
    publicKeyField.setAccessible(true);
    Assertions.assertEquals(secpPublicKey, publicKeyField.get(packet));
    publicKeyField.setAccessible(false);
  }

  @Test
  public void testCreateFindNeighborsPacketWithNodeKey() throws IllegalAccessException {
    final FindNeighborsPacketData packetData = Mockito.mock(FindNeighborsPacketData.class);
    final NodeKey nodeKey = Mockito.mock(NodeKey.class);
    final SECPSignature secpSignature = Mockito.mock(SECPSignature.class);
    final SECPPublicKey secpPublicKey = Mockito.mock(SECPPublicKey.class);

    Mockito.doAnswer(
            (invocationOnMock) -> {
              RLPOutput out = invocationOnMock.getArgument(1, RLPOutput.class);
              out.startList();
              out.writeBytes(Bytes.repeat((byte) 0xbb, 32));
              out.endList();
              return null;
            })
        .when(findNeighborsPacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));

    Mockito.when(nodeKey.sign(Mockito.any(Bytes32.class))).thenReturn(secpSignature);
    Mockito.when(packetSignatureEncoder.encodeSignature(secpSignature))
        .thenReturn(Bytes.fromHexString("0xeeeeeeeeeeeeeeee"));
    Mockito.when(nodeKey.getPublicKey()).thenReturn(secpPublicKey);

    Packet packet = factory.create(PacketType.FIND_NEIGHBORS, packetData, nodeKey);

    Mockito.verify(findNeighborsPacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));
    ArgumentCaptor<Bytes32> typeAndDataBytesHashCaptor = ArgumentCaptor.forClass(Bytes32.class);
    Mockito.verify(nodeKey).sign(typeAndDataBytesHashCaptor.capture());
    Mockito.verify(packetSignatureEncoder).encodeSignature(secpSignature);
    Mockito.verify(nodeKey).getPublicKey();
    Mockito.verifyNoInteractions(
        pingPacketDataRlpWriter,
        pongPacketDataRlpWriter,
        neighborsPacketDataRlpWriter,
        enrRequestPacketDataRlpWriter,
        enrResponsePacketDataRlpWriter,
        signatureAlgorithm);

    Assertions.assertEquals(
        "0x66c5b5be0b368658d70620b67f819df01fa6f235c434f7797f749cd27c1d5694",
        typeAndDataBytesHashCaptor.getValue().toHexString());

    Assertions.assertNotNull(packet);
    Assertions.assertEquals(PacketType.FIND_NEIGHBORS, packet.getType());
    Assertions.assertEquals(packetData, packet.getPacketData());
    Assertions.assertEquals(
        "0xb8d306ac23f97ebc4a3a477ebb7f47230e581cc4bf4a03a2c4f12e668095fc93",
        packet.getHash().toHexString());
    Assertions.assertEquals(secpSignature, packet.getSignature());
    Field publicKeyField =
        ReflectionUtils.findFields(
                Packet.class,
                (f) -> f.getName().equals("publicKey"),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
            .getFirst();
    publicKeyField.setAccessible(true);
    Assertions.assertEquals(secpPublicKey, publicKeyField.get(packet));
    publicKeyField.setAccessible(false);
  }

  @Test
  public void testCreateNeighborsPacketWithNodeKey() throws IllegalAccessException {
    final NeighborsPacketData packetData = Mockito.mock(NeighborsPacketData.class);
    final NodeKey nodeKey = Mockito.mock(NodeKey.class);
    final SECPSignature secpSignature = Mockito.mock(SECPSignature.class);
    final SECPPublicKey secpPublicKey = Mockito.mock(SECPPublicKey.class);

    Mockito.doAnswer(
            (invocationOnMock) -> {
              RLPOutput out = invocationOnMock.getArgument(1, RLPOutput.class);
              out.startList();
              out.writeBytes(Bytes.repeat((byte) 0xbb, 32));
              out.endList();
              return null;
            })
        .when(neighborsPacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));

    Mockito.when(nodeKey.sign(Mockito.any(Bytes32.class))).thenReturn(secpSignature);
    Mockito.when(packetSignatureEncoder.encodeSignature(secpSignature))
        .thenReturn(Bytes.fromHexString("0xeeeeeeeeeeeeeeee"));
    Mockito.when(nodeKey.getPublicKey()).thenReturn(secpPublicKey);

    Packet packet = factory.create(PacketType.NEIGHBORS, packetData, nodeKey);

    Mockito.verify(neighborsPacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));
    ArgumentCaptor<Bytes32> typeAndDataBytesHashCaptor = ArgumentCaptor.forClass(Bytes32.class);
    Mockito.verify(nodeKey).sign(typeAndDataBytesHashCaptor.capture());
    Mockito.verify(packetSignatureEncoder).encodeSignature(secpSignature);
    Mockito.verify(nodeKey).getPublicKey();
    Mockito.verifyNoInteractions(
        pingPacketDataRlpWriter,
        pongPacketDataRlpWriter,
        findNeighborsPacketDataRlpWriter,
        enrRequestPacketDataRlpWriter,
        enrResponsePacketDataRlpWriter,
        signatureAlgorithm);

    Assertions.assertEquals(
        "0x9bba8554a37d5ecaac3e85483f7cb4be96cfa82bb1be8fb3bb01c9bcb76d35e6",
        typeAndDataBytesHashCaptor.getValue().toHexString());

    Assertions.assertNotNull(packet);
    Assertions.assertEquals(PacketType.NEIGHBORS, packet.getType());
    Assertions.assertEquals(packetData, packet.getPacketData());
    Assertions.assertEquals(
        "0x16395ac0538f94709c73575da835305ccaa7c62f8e578282af5168971f3d795e",
        packet.getHash().toHexString());
    Assertions.assertEquals(secpSignature, packet.getSignature());
    Field publicKeyField =
        ReflectionUtils.findFields(
                Packet.class,
                (f) -> f.getName().equals("publicKey"),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
            .getFirst();
    publicKeyField.setAccessible(true);
    Assertions.assertEquals(secpPublicKey, publicKeyField.get(packet));
    publicKeyField.setAccessible(false);
  }

  @Test
  public void testCreateEnrRequestPacketWithNodeKey() throws IllegalAccessException {
    final EnrRequestPacketData packetData = Mockito.mock(EnrRequestPacketData.class);
    final NodeKey nodeKey = Mockito.mock(NodeKey.class);
    final SECPSignature secpSignature = Mockito.mock(SECPSignature.class);
    final SECPPublicKey secpPublicKey = Mockito.mock(SECPPublicKey.class);

    Mockito.doAnswer(
            (invocationOnMock) -> {
              RLPOutput out = invocationOnMock.getArgument(1, RLPOutput.class);
              out.startList();
              out.writeBytes(Bytes.repeat((byte) 0xbb, 32));
              out.endList();
              return null;
            })
        .when(enrRequestPacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));

    Mockito.when(nodeKey.sign(Mockito.any(Bytes32.class))).thenReturn(secpSignature);
    Mockito.when(packetSignatureEncoder.encodeSignature(secpSignature))
        .thenReturn(Bytes.fromHexString("0xeeeeeeeeeeeeeeee"));
    Mockito.when(nodeKey.getPublicKey()).thenReturn(secpPublicKey);

    Packet packet = factory.create(PacketType.ENR_REQUEST, packetData, nodeKey);

    Mockito.verify(enrRequestPacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));
    ArgumentCaptor<Bytes32> typeAndDataBytesHashCaptor = ArgumentCaptor.forClass(Bytes32.class);
    Mockito.verify(nodeKey).sign(typeAndDataBytesHashCaptor.capture());
    Mockito.verify(packetSignatureEncoder).encodeSignature(secpSignature);
    Mockito.verify(nodeKey).getPublicKey();
    Mockito.verifyNoInteractions(
        pingPacketDataRlpWriter,
        pongPacketDataRlpWriter,
        findNeighborsPacketDataRlpWriter,
        neighborsPacketDataRlpWriter,
        enrResponsePacketDataRlpWriter,
        signatureAlgorithm);

    Assertions.assertEquals(
        "0x4005740364c45fe432c2332c80aa989c9ade2c020785634c6f8650189378da89",
        typeAndDataBytesHashCaptor.getValue().toHexString());

    Assertions.assertNotNull(packet);
    Assertions.assertEquals(PacketType.ENR_REQUEST, packet.getType());
    Assertions.assertEquals(packetData, packet.getPacketData());
    Assertions.assertEquals(
        "0x482d0816a45ace4eb2d3dca77ae207f31b093a5264d723ac793d9065286c0454",
        packet.getHash().toHexString());
    Assertions.assertEquals(secpSignature, packet.getSignature());
    Field publicKeyField =
        ReflectionUtils.findFields(
                Packet.class,
                (f) -> f.getName().equals("publicKey"),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
            .getFirst();
    publicKeyField.setAccessible(true);
    Assertions.assertEquals(secpPublicKey, publicKeyField.get(packet));
    publicKeyField.setAccessible(false);
  }

  @Test
  public void testCreateEnrResponsePacketWithNodeKey() throws IllegalAccessException {
    final EnrResponsePacketData packetData = Mockito.mock(EnrResponsePacketData.class);
    final NodeKey nodeKey = Mockito.mock(NodeKey.class);
    final SECPSignature secpSignature = Mockito.mock(SECPSignature.class);
    final SECPPublicKey secpPublicKey = Mockito.mock(SECPPublicKey.class);

    Mockito.doAnswer(
            (invocationOnMock) -> {
              RLPOutput out = invocationOnMock.getArgument(1, RLPOutput.class);
              out.startList();
              out.writeBytes(Bytes.repeat((byte) 0xbb, 32));
              out.endList();
              return null;
            })
        .when(enrResponsePacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));

    Mockito.when(nodeKey.sign(Mockito.any(Bytes32.class))).thenReturn(secpSignature);
    Mockito.when(packetSignatureEncoder.encodeSignature(secpSignature))
        .thenReturn(Bytes.fromHexString("0xeeeeeeeeeeeeeeee"));
    Mockito.when(nodeKey.getPublicKey()).thenReturn(secpPublicKey);

    Packet packet = factory.create(PacketType.ENR_RESPONSE, packetData, nodeKey);

    Mockito.verify(enrResponsePacketDataRlpWriter)
        .writeTo(Mockito.eq(packetData), Mockito.any(RLPOutput.class));
    ArgumentCaptor<Bytes32> typeAndDataBytesHashCaptor = ArgumentCaptor.forClass(Bytes32.class);
    Mockito.verify(nodeKey).sign(typeAndDataBytesHashCaptor.capture());
    Mockito.verify(packetSignatureEncoder).encodeSignature(secpSignature);
    Mockito.verify(nodeKey).getPublicKey();
    Mockito.verifyNoInteractions(
        pingPacketDataRlpWriter,
        pongPacketDataRlpWriter,
        findNeighborsPacketDataRlpWriter,
        neighborsPacketDataRlpWriter,
        enrRequestPacketDataRlpWriter,
        signatureAlgorithm);

    Assertions.assertEquals(
        "0x141c592ac04e806c77621d9a6e3885f2a0b6e9ecd2ea9e069ade4a965f037f04",
        typeAndDataBytesHashCaptor.getValue().toHexString());

    Assertions.assertNotNull(packet);
    Assertions.assertEquals(PacketType.ENR_RESPONSE, packet.getType());
    Assertions.assertEquals(packetData, packet.getPacketData());
    Assertions.assertEquals(
        "0xf610ac4f0ef89cdcc65a7c2369d29fc4df3f6c197fb1faab63246be3c1f55844",
        packet.getHash().toHexString());
    Assertions.assertEquals(secpSignature, packet.getSignature());
    Field publicKeyField =
        ReflectionUtils.findFields(
                Packet.class,
                (f) -> f.getName().equals("publicKey"),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
            .getFirst();
    publicKeyField.setAccessible(true);
    Assertions.assertEquals(secpPublicKey, publicKeyField.get(packet));
    publicKeyField.setAccessible(false);
  }

  @Test
  public void testCreateFromMessage() throws IllegalAccessException {
    final PacketType packetType = PacketType.PING;
    final PacketData packetData = Mockito.mock(PacketData.class);
    final Bytes encodedSignature =
        Bytes.repeat((byte) 0x02, Packet.PACKET_TYPE_INDEX - Packet.SIGNATURE_INDEX);
    final Bytes signedPayload = Bytes.repeat((byte) 0x03, 128);
    final Bytes hash = Hash.keccak256(Bytes.concatenate(encodedSignature, signedPayload));
    final Bytes message = Bytes.concatenate(hash, encodedSignature, signedPayload);
    final SECPSignature secpSignature = Mockito.mock(SECPSignature.class);
    final SECPPublicKey secpPublicKey = Mockito.mock(SECPPublicKey.class);

    Mockito.when(
            signatureAlgorithm.createSignature(
                Bytes.repeat((byte) 0x02, 32).toUnsignedBigInteger(),
                Bytes.repeat((byte) 0x02, 32).toUnsignedBigInteger(),
                (byte) 0x02))
        .thenReturn(secpSignature);
    Mockito.when(
            signatureAlgorithm.recoverPublicKeyFromSignature(
                Hash.keccak256(signedPayload), secpSignature))
        .thenReturn(Optional.of(secpPublicKey));

    Packet packet = factory.create(packetType, packetData, message);

    Mockito.verify(signatureAlgorithm)
        .createSignature(
            Bytes.repeat((byte) 0x02, 32).toUnsignedBigInteger(),
            Bytes.repeat((byte) 0x02, 32).toUnsignedBigInteger(),
            (byte) 0x02);
    Mockito.verify(signatureAlgorithm)
        .recoverPublicKeyFromSignature(Hash.keccak256(signedPayload), secpSignature);
    Mockito.verifyNoInteractions(
        pingPacketDataRlpWriter,
        pongPacketDataRlpWriter,
        findNeighborsPacketDataRlpWriter,
        neighborsPacketDataRlpWriter,
        enrRequestPacketDataRlpWriter,
        enrResponsePacketDataRlpWriter,
        packetSignatureEncoder);

    Assertions.assertNotNull(packet);
    Assertions.assertEquals(packetType, packet.getType());
    Assertions.assertEquals(packetData, packet.getPacketData());
    Assertions.assertEquals(hash, packet.getHash());
    Assertions.assertEquals(secpSignature, packet.getSignature());
    Field publicKeyField =
        ReflectionUtils.findFields(
                Packet.class,
                (f) -> f.getName().equals("publicKey"),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
            .getFirst();
    publicKeyField.setAccessible(true);
    Assertions.assertEquals(secpPublicKey, publicKeyField.get(packet));
    publicKeyField.setAccessible(false);
  }
}
