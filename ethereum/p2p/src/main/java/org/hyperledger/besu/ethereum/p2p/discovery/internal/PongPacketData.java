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
import org.hyperledger.besu.ethereum.rlp.MalformedRLPInputException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PongPacketData implements PacketData {

  /* Destination. */
  private final Endpoint to;

  /* Hash of the PING packet. */
  private final Bytes pingHash;

  /* In seconds after epoch. */
  private final long expiration;

  /* Current sequence number of the sending node’s record */
  private final UInt64 enrSeq;
  private static final Logger LOG = LoggerFactory.getLogger(PongPacketData.class);

  private PongPacketData(
      final Endpoint to, final Bytes pingHash, final long expiration, final UInt64 enrSeq) {
    this.to = to;
    this.pingHash = pingHash;
    this.expiration = expiration;
    this.enrSeq = enrSeq;
  }

  public static PongPacketData create(
      final Endpoint to, final Bytes pingHash, final UInt64 enrSeq) {
    return new PongPacketData(to, pingHash, PacketData.defaultExpiration(), enrSeq);
  }

  public static PongPacketData readFrom(final RLPInput in) {
    in.enterList();
    final Endpoint to = Endpoint.decodeStandalone(in);
    final Bytes hash = in.readBytes();
    final long expiration = in.readLongScalar();
    UInt64 enrSeq = null;
    if (!in.isEndOfCurrentList()) {
      try {
        enrSeq = UInt64.valueOf(in.readBigIntegerScalar());
        LOG.debug("read PONG enr from scalar");
      } catch (MalformedRLPInputException malformed) {
        LOG.debug("failed to read PONG enr from scalar, trying as byte array");
        enrSeq = UInt64.fromBytes(in.readBytes());
      }
    }
    in.leaveListLenient();
    return new PongPacketData(to, hash, expiration, enrSeq);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    to.encodeStandalone(out);
    out.writeBytes(pingHash);
    out.writeLongScalar(expiration);
    out.writeBigIntegerScalar(enrSeq.toBigInteger());
    out.endList();
  }

  /**
   * Used by test classes to read legacy encodes of Pongs which used a byte array for the ENR field
   *
   * @deprecated Only to be used by internal tests to confirm backward compatibility.
   * @param in input stream being read from
   * @return PongPacketData parsed from input, using legacy encode
   */
  @Deprecated
  public static PongPacketData legacyReadFrom(final RLPInput in) {
    in.enterList();
    final Endpoint to = Endpoint.decodeStandalone(in);
    final Bytes hash = in.readBytes();
    final long expiration = in.readLongScalar();
    UInt64 enrSeq = null;
    if (!in.isEndOfCurrentList()) {
      enrSeq = UInt64.fromBytes(in.readBytes());
    }
    in.leaveListLenient();
    return new PongPacketData(to, hash, expiration, enrSeq);
  }

  /**
   * @deprecated Only to be used by internal tests to confirm backward compatibility.
   * @param out output stream being written to
   */
  @Deprecated
  public void legacyWriteTo(final RLPOutput out) {
    out.startList();
    to.encodeStandalone(out);
    out.writeBytes(pingHash);
    out.writeLongScalar(expiration);
    out.writeBytes(
        getEnrSeq()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Attempting to serialize invalid PONG packet. Missing 'enrSeq' field"))
            .toBytes());
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
        + ", enrSeq="
        + enrSeq
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

  public Optional<UInt64> getEnrSeq() {
    return Optional.ofNullable(enrSeq);
  }
}
