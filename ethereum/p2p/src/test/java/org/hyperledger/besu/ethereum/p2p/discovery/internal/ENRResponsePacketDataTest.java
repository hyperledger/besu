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
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.Test;

public class ENRResponsePacketDataTest {
  @Test
  public void serializeDeserialize() {
    final Bytes requestHash = Bytes.fromHexStringLenient("0x1234");
    final NodeRecord nodeRecord =
        new NodeRecordFactory(IdentitySchemaInterpreter.V4)
            .createFromValues(UInt64.ONE, new EnrField(EnrField.ID, IdentitySchema.V4));

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
    final NodeRecord nodeRecord =
        new NodeRecordFactory(IdentitySchemaInterpreter.V4)
            .createFromValues(UInt64.ONE, new EnrField(EnrField.ID, IdentitySchema.V4));

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytes(requestHash);
    out.writeBytes(nodeRecord.serialize());
    out.endList();
    final Bytes encoded = out.encoded();

    final ENRResponsePacketData deserialized = ENRResponsePacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getRequestHash()).isEqualTo(requestHash);
    assertThat(deserialized.getEnr()).isEqualTo(nodeRecord);
  }

  @Test
  public void readFrom_withExtraFields() {
    final Bytes requestHash = Bytes.fromHexStringLenient("0x1234");
    final NodeRecord nodeRecord =
        new NodeRecordFactory(IdentitySchemaInterpreter.V4)
            .createFromValues(UInt64.ONE, new EnrField(EnrField.ID, IdentitySchema.V4));

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytes(requestHash);
    out.writeBytes(nodeRecord.serialize());
    // Add random fields
    out.writeLong(1234L);
    out.endList();
    final Bytes encoded = out.encoded();

    final ENRResponsePacketData deserialized = ENRResponsePacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getRequestHash()).isEqualTo(requestHash);
    assertThat(deserialized.getEnr()).isEqualTo(nodeRecord);
  }
}
