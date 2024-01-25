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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;

public class ENRResponsePacketDataTest {
  @Test
  public void serializeDeserialize() {
    final Bytes requestHash = Bytes.fromHexStringLenient("0x1234");
    final Bytes nodeId =
        Bytes.fromHexString("a448f24c6d18e575453db13171562b71999873db5b286df957af199ec94617f7");
    final SECP256K1.SecretKey privateKey =
        SECP256K1.SecretKey.fromBytes(
            Bytes32.fromHexString(
                "b71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f291"));

    NodeRecord nodeRecord =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.ONE,
            new EnrField(EnrField.ID, IdentitySchema.V4),
            new EnrField(EnrField.IP_V4, Bytes.fromHexString("0x7F000001")),
            new EnrField(EnrField.IP_V6, Bytes.fromHexString("0x00000001")),
            new EnrField(EnrField.UDP, 30303),
            new EnrField(EnrField.UDP_V6, 30303),
            new EnrField(EnrField.TCP, 8080),
            new EnrField(EnrField.TCP_V6, 8080),
            new EnrField(
                EnrField.PKEY_SECP256K1,
                Functions.deriveCompressedPublicKeyFromPrivate(privateKey)));
    nodeRecord.sign(privateKey);

    assertThat(nodeRecord.getNodeId()).isEqualTo(nodeId);
    assertThat(nodeRecord.asEnr())
        .isEqualTo(
            "enr:-KS4QHWjNgmcnxf-dwC_paPSLEi1N-eW0Swoa4lLNOLe09UOEZ4qoDy3a8dl8wmprsu84JMFzvdc-WZrrqW"
                + "efDWpf3IBgmlkgnY0gmlwhH8AAAGDaXA2hAAAAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0R"
                + "W_QAdpzBQA8yWM0xOIN0Y3CCH5CEdGNwNoIfkIN1ZHCCdl-EdWRwNoJ2Xw");

    final ENRResponsePacketData packet = ENRResponsePacketData.create(requestHash, nodeRecord);
    final Bytes serialized = RLP.encode(packet::writeTo);
    final ENRResponsePacketData deserialized =
        ENRResponsePacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getRequestHash()).isEqualTo(requestHash);
    assertThat(deserialized.getEnr()).isEqualTo(nodeRecord);
  }

  @Test
  public void readFrom() {
    final Bytes requestHash = Bytes.fromHexStringLenient("0x1234");
    final Bytes nodeId =
        Bytes.fromHexString("a448f24c6d18e575453db13171562b71999873db5b286df957af199ec94617f7");
    final SECP256K1.SecretKey privateKey =
        SECP256K1.SecretKey.fromBytes(
            Bytes32.fromHexString(
                "b71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f291"));

    NodeRecord nodeRecord =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.ONE,
            new EnrField(EnrField.ID, IdentitySchema.V4),
            new EnrField(EnrField.IP_V4, Bytes.fromHexString("0x7F000001")),
            new EnrField(EnrField.UDP, 30303),
            new EnrField(
                EnrField.PKEY_SECP256K1,
                Functions.deriveCompressedPublicKeyFromPrivate(privateKey)));
    nodeRecord.sign(privateKey);

    assertThat(nodeRecord.getNodeId()).isEqualTo(nodeId);
    assertThat(nodeRecord.asEnr())
        .isEqualTo(
            "enr:-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33L"
                + "s8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8y"
                + "WM0xOIN1ZHCCdl8");

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytes(requestHash);
    out.writeRLPBytes(nodeRecord.serialize());
    out.endList();
    final Bytes encoded = out.encoded();

    final ENRResponsePacketData deserialized = ENRResponsePacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getRequestHash()).isEqualTo(requestHash);
    assertThat(deserialized.getEnr()).isEqualTo(nodeRecord);
  }

  @Test
  public void writeTo() {
    final Bytes requestHash = Bytes.fromHexStringLenient("0x1234");
    final Bytes nodeId =
        Bytes.fromHexString("a448f24c6d18e575453db13171562b71999873db5b286df957af199ec94617f7");
    final SECP256K1.SecretKey privateKey =
        SECP256K1.SecretKey.fromBytes(
            Bytes32.fromHexString(
                "b71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f291"));

    NodeRecord nodeRecord =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.ONE,
            new EnrField(EnrField.ID, IdentitySchema.V4),
            new EnrField(EnrField.IP_V4, Bytes.fromHexString("0x7F000001")),
            new EnrField(EnrField.UDP, 30303),
            new EnrField(
                EnrField.PKEY_SECP256K1,
                Functions.deriveCompressedPublicKeyFromPrivate(privateKey)));
    nodeRecord.sign(privateKey);

    assertThat(nodeRecord.getNodeId()).isEqualTo(nodeId);
    assertThat(nodeRecord.asEnr())
        .isEqualTo(
            "enr:-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33L"
                + "s8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8y"
                + "WM0xOIN1ZHCCdl8");

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    final ENRResponsePacketData packet = ENRResponsePacketData.create(requestHash, nodeRecord);
    packet.writeTo(out);
    final Bytes encoded = out.encoded();

    final ENRResponsePacketData deserialized = ENRResponsePacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getRequestHash()).isEqualTo(requestHash);
    assertThat(deserialized.getEnr()).isEqualTo(nodeRecord);
  }

  @Test
  public void readFrom_withExtraFields() {
    final Bytes requestHash = Bytes.fromHexStringLenient("0x1234");
    final Bytes nodeId =
        Bytes.fromHexString("a448f24c6d18e575453db13171562b71999873db5b286df957af199ec94617f7");
    final SECP256K1.SecretKey privateKey =
        SECP256K1.SecretKey.fromBytes(
            Bytes32.fromHexString(
                "b71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f291"));

    NodeRecord nodeRecord =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.ONE,
            new EnrField(EnrField.ID, IdentitySchema.V4),
            new EnrField(EnrField.IP_V4, Bytes.fromHexString("0x7F000001")),
            new EnrField(EnrField.UDP, 30303),
            new EnrField(
                EnrField.PKEY_SECP256K1,
                Functions.deriveCompressedPublicKeyFromPrivate(privateKey)),
            new EnrField("foo", Bytes.fromHexString("0x1234")));
    nodeRecord.sign(privateKey);

    assertThat(nodeRecord.getNodeId()).isEqualTo(nodeId);
    assertThat(nodeRecord.asEnr())
        .isEqualTo(
            "enr:-Iu4QDokK026ShDdi-PmzNgTr-oaQEslAfoLuphwEznSx0xsVwD0KZV1m7k4enZpf0aEQmCYWZOgD4kptYE"
                + "Fo2QKX28Bg2Zvb4ISNIJpZIJ2NIJpcIR_AAABiXNlY3AyNTZrMaEDymNMrg1JrLQB2KTGtv6MVbcNEVv0"
                + "AHacwUAPMljNMTiDdWRwgnZf");

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytes(requestHash);
    out.writeRLPBytes(nodeRecord.serialize());
    // Add random fields
    out.writeLong(1234L);
    out.endList();
    final Bytes encoded = out.encoded();

    final ENRResponsePacketData deserialized = ENRResponsePacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getRequestHash()).isEqualTo(requestHash);
    assertThat(deserialized.getEnr()).isEqualTo(nodeRecord);
  }

  @Test
  public void readFrom_invalidSignature() {
    final Bytes requestHash = Bytes.fromHexStringLenient("0x1234");
    final SECP256K1.SecretKey privateKey =
        SECP256K1.SecretKey.fromBytes(
            Bytes32.fromHexString(
                "b71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f292"));

    NodeRecord nodeRecord =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.ONE,
            new EnrField(EnrField.ID, IdentitySchema.V4),
            new EnrField(EnrField.IP_V4, Bytes.fromHexString("0x7F000001")),
            new EnrField(EnrField.UDP, 30303),
            new EnrField(
                EnrField.PKEY_SECP256K1,
                Functions.deriveCompressedPublicKeyFromPrivate(privateKey)));
    nodeRecord.sign(privateKey);
    nodeRecord.set(EnrField.UDP, 1234);

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytes(requestHash);
    out.writeRLPBytes(nodeRecord.serialize());
    out.endList();
    final Bytes encoded = out.encoded();

    final ENRResponsePacketData deserialized = ENRResponsePacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getRequestHash()).isEqualTo(requestHash);
    assertThat(deserialized.getEnr()).isEqualTo(nodeRecord);
    assertThat(deserialized.getEnr().isValid()).isFalse();
  }
}
