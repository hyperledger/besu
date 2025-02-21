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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.ping;

import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.PacketData;

import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt64;

public class PingPacketData implements PacketData {

  /* Fixed value that represents we're using v5 of the P2P discovery protocol. */
  public static final int VERSION = 5;

  /* Source. If the field is garbage this is empty and we might need to recover it another way. From our bonded peers, for example. */
  private final Optional<Endpoint> maybeFrom;

  /* Destination. */
  private final Endpoint to;

  /* In seconds after epoch. */
  private final long expiration;

  /* Current sequence number of the sending node’s record */
  private final UInt64 enrSeq;

  PingPacketData(
      final Optional<Endpoint> maybeFrom,
      final Endpoint to,
      final long expiration,
      final UInt64 enrSeq) {
    this.maybeFrom = maybeFrom;
    this.to = to;
    this.expiration = expiration;
    this.enrSeq = enrSeq;
  }

  public Optional<Endpoint> getFrom() {
    return maybeFrom;
  }

  public Endpoint getTo() {
    return to;
  }

  public long getExpiration() {
    return expiration;
  }

  public Optional<UInt64> getEnrSeq() {
    return Optional.ofNullable(enrSeq);
  }

  @Override
  public String toString() {
    return "PingPacketData{"
        + "from="
        + maybeFrom.map(Object::toString).orElse("INVALID")
        + ", to="
        + to
        + ", expiration="
        + expiration
        + ", enrSeq="
        + enrSeq
        + '}';
  }
}
