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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.pong;

import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.PacketData;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;

public class PongPacketData implements PacketData {

  /* Destination. */
  private final Endpoint to;

  /* Hash of the PING packet. */
  private final Bytes pingHash;

  /* In seconds after epoch. */
  private final long expiration;

  /* Current sequence number of the sending node’s record */
  private final UInt64 enrSeq;

  PongPacketData(
      final Endpoint to, final Bytes pingHash, final long expiration, final UInt64 enrSeq) {
    this.to = to;
    this.pingHash = pingHash;
    this.expiration = expiration;
    this.enrSeq = enrSeq;
  }

  public Endpoint getTo() {
    return to;
  }

  public Bytes getPingHash() {
    return pingHash;
  }

  public long getExpiration() {
    return expiration;
  }

  public Optional<UInt64> getEnrSeq() {
    return Optional.ofNullable(enrSeq);
  }

  @Override
  public String toString() {
    return "PongPacketData{"
        + "to="
        + to
        + ", pingHash="
        + pingHash
        + ", expiration="
        + expiration
        + ", enrSeq="
        + enrSeq
        + '}';
  }
}
