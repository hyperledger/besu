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

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.RlpUtil;

public class ENRResponsePacketData implements PacketData {
  /* The hash of the entire ENRRequest packet being replied to. */
  private final Bytes requestHash;

  /* The node record. */
  private final NodeRecord enr;

  private ENRResponsePacketData(final Bytes requestHash, final NodeRecord enr) {
    checkArgument(requestHash != null, "request hash cannot be null");
    checkArgument(enr != null, "enr cannot be null");

    this.requestHash = requestHash;
    this.enr = enr;
  }

  public static ENRResponsePacketData create(final Bytes requestHash, final NodeRecord enr) {
    return new ENRResponsePacketData(requestHash, enr);
  }

  public static ENRResponsePacketData readFrom(final RLPInput in) {
    in.enterList();
    final Bytes requestHash = in.readBytes();
    in.enterList();
    final Bytes signature = in.readBytes();
    final UInt64 sequence = UInt64.fromBytes(RlpUtil.decodeSingleString(in.readBytes()));
    List<EnrField> enrFields = new ArrayList<>();
    while (!in.isEndOfCurrentList()) {
      String key = new String(in.readBytes().toArray(), StandardCharsets.UTF_8);
      if (key.equals(EnrField.ID)) {
        enrFields.add(
            new EnrField(
                key,
                IdentitySchema.fromString(
                    new String(in.readBytes().toArray(), StandardCharsets.UTF_8))));
      } else if (key.equals(EnrField.TCP)
          || key.equals(EnrField.UDP)
          || key.equals(EnrField.TCP_V6)
          || key.equals(EnrField.UDP_V6)) {
        enrFields.add(new EnrField(key, in.readIntScalar()));
      } else {
        enrFields.add(new EnrField(key, in.readBytes()));
      }
    }
    in.leaveList();
    in.leaveListLenient();

    final NodeRecord enr = NodeRecordFactory.DEFAULT.createFromValues(sequence, enrFields);
    enr.setSignature(signature);

    return new ENRResponsePacketData(requestHash, enr);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeBytes(requestHash);
    out.writeRLPBytes(enr.serialize());
    out.endList();
  }

  public Bytes getRequestHash() {
    return requestHash;
  }

  public NodeRecord getEnr() {
    return enr;
  }

  @Override
  public String toString() {
    return "ENRResponsePacketData{" + "requestHash=" + requestHash + ", enr=" + enr + '}';
  }
}
