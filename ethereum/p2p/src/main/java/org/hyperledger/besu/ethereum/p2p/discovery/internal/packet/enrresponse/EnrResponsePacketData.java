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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.enrresponse;

import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.PacketData;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class EnrResponsePacketData implements PacketData {
  /* The hash of the entire ENRRequest packet being replied to. */
  private final Bytes requestHash;

  /* The node record. */
  private final NodeRecord enr;

  EnrResponsePacketData(final Bytes requestHash, final NodeRecord enr) {
    this.requestHash = requestHash;
    this.enr = enr;
  }

  public Bytes getRequestHash() {
    return requestHash;
  }

  public NodeRecord getEnr() {
    return enr;
  }

  @Override
  public String toString() {
    return "EnrResponsePacketData{" + "requestHash=" + requestHash + ", enr=" + enr + '}';
  }
}
