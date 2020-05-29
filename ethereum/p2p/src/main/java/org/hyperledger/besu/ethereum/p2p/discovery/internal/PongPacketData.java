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

import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;

public class PongPacketData implements PacketData {

  /* Destination. */
  private final Endpoint to;

  /* Hash of the PING packet. */
  private final Bytes pingHash;

  /* In millis after epoch. */
  private final long expiration;

  private PongPacketData(final Endpoint to, final Bytes pingHash, final long expiration) {
    this.to = to;
    this.pingHash = pingHash;
    this.expiration = expiration;
  }

  public static PongPacketData create(final Endpoint to, final Bytes pingHash) {
    return new PongPacketData(to, pingHash, PacketData.defaultExpiration());
  }

  public static PongPacketData readFrom(final RLPInput in) {
    in.enterList();
    final Endpoint to = Endpoint.decodeStandalone(in);
    final Bytes hash = in.readBytes();
    final long expiration = in.readLongScalar();
    in.leaveListLenient();
    return new PongPacketData(to, hash, expiration);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    to.encodeStandalone(out);
    out.writeBytes(pingHash);
    out.writeLongScalar(expiration);
    out.endList();
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
        + '}';
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
}
