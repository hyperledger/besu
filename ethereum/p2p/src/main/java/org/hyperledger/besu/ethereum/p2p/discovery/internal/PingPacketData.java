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

import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.rlp.MalformedRLPInputException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingPacketData implements PacketData {

  /* Fixed value that represents we're using v5 of the P2P discovery protocol. */
  private static final int VERSION = 5;

  /* Source. If the field is garbage this is empty and we might need to recover it another way. From our bonded peers, for example. */
  private final Optional<Endpoint> maybeFrom;

  /* Destination. */
  private final Endpoint to;

  /* In seconds after epoch. */
  private final long expiration;

  /* Current sequence number of the sending node’s record */
  private final UInt64 enrSeq;
  private static final Logger LOG = LoggerFactory.getLogger(PingPacketData.class);

  private PingPacketData(
      final Optional<Endpoint> maybeFrom,
      final Endpoint to,
      final long expiration,
      final UInt64 enrSeq) {
    checkArgument(to != null, "destination endpoint cannot be null");
    checkArgument(expiration >= 0, "expiration cannot be negative");

    this.maybeFrom = maybeFrom;
    this.to = to;
    this.expiration = expiration;
    this.enrSeq = enrSeq;
  }

  public static PingPacketData create(
      final Optional<Endpoint> from, final Endpoint to, final UInt64 enrSeq) {
    checkArgument(
        enrSeq != null && UInt64.ZERO.compareTo(enrSeq) < 0, "enrSeq cannot be null or negative");
    return create(from, to, PacketData.defaultExpiration(), enrSeq);
  }

  static PingPacketData create(
      final Optional<Endpoint> from,
      final Endpoint to,
      final long expirationSec,
      final UInt64 enrSeq) {
    checkArgument(
        enrSeq != null && UInt64.ZERO.compareTo(enrSeq) < 0, "enrSeq cannot be null or negative");
    return new PingPacketData(from, to, expirationSec, enrSeq);
  }

  public static PingPacketData readFrom(final RLPInput in) {
    in.enterList();
    // The first element signifies the "version", but this value is ignored as of EIP-8
    in.readBigIntegerScalar();
    Optional<Endpoint> from = Optional.empty();
    Optional<Endpoint> to = Optional.empty();
    if (in.nextIsList()) {
      to = Endpoint.maybeDecodeStandalone(in);
      // https://github.com/ethereum/devp2p/blob/master/discv4.md#ping-packet-0x01
      if (in.nextIsList()) { // if there are two, the first is the from address, next is the to
        // address
        from = to;
        to = Endpoint.maybeDecodeStandalone(in);
      }
    } else {
      throw new DevP2PException("missing address in ping packet");
    }
    final long expiration = in.readLongScalar();
    UInt64 enrSeq = null;
    if (!in.isEndOfCurrentList()) {
      try {
        enrSeq = UInt64.valueOf(in.readBigIntegerScalar());
        LOG.debug("read PING enr as long scalar");
      } catch (MalformedRLPInputException malformed) {
        LOG.debug("failed to read PING enr as scalar, trying to read bytes instead");
        enrSeq = UInt64.fromBytes(in.readBytes());
      }
    }
    in.leaveListLenient();
    return new PingPacketData(from, to.get(), expiration, enrSeq);
  }

  /**
   * Used by test classes to read legacy encodes of Pings which used a byte array for the ENR field
   *
   * @deprecated Only to be used by internal tests to confirm backward compatibility.
   * @param in input stream to read from
   * @return PingPacketData parsed from input, using legacy encode.
   */
  @Deprecated
  public static PingPacketData legacyReadFrom(final RLPInput in) { // only for testing, do not use
    in.enterList();
    // The first element signifies the "version", but this value is ignored as of EIP-8
    in.readBigIntegerScalar();
    final Optional<Endpoint> from = Endpoint.maybeDecodeStandalone(in);
    final Endpoint to = Endpoint.decodeStandalone(in);
    final long expiration = in.readLongScalar();
    UInt64 enrSeq = null;
    if (!in.isEndOfCurrentList()) {
      enrSeq = UInt64.fromBytes(in.readBytes());
    }
    in.leaveListLenient();
    return new PingPacketData(from, to, expiration, enrSeq);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeIntScalar(VERSION);
    if (maybeFrom.isPresent()) {
      maybeFrom.get().encodeStandalone(out);
    }
    to.encodeStandalone(out);
    out.writeLongScalar(expiration);
    out.writeBigIntegerScalar(enrSeq.toBigInteger());
    out.endList();
  }

  /**
   * @deprecated Only to be used by internal tests to confirm backward compatibility.
   * @param out stream to write to
   */
  @Deprecated
  public void legacyWriteTo(final RLPOutput out) {
    out.startList();
    out.writeIntScalar(VERSION);
    maybeFrom
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Attempting to serialize invalid PING packet. Missing 'from' field"))
        .encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(expiration);
    out.writeBytes(
        getEnrSeq()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Attempting to serialize invalid PING packet. Missing 'enrSeq' field"))
            .toBytes());
    out.endList();
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
