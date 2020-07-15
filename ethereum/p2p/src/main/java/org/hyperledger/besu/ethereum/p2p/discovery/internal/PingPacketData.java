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
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Optional;

public class PingPacketData implements PacketData {

  /* Fixed value that represents we're using v5 of the P2P discovery protocol. */
  private static final int VERSION = 5;

  /* Source. If the field is garbage this is empty and we might need to recover it another way. From our bonded peers, for example. */
  private final Optional<Endpoint> maybeFrom;

  /* Destination. */
  private final Endpoint to;

  /* In millis after epoch. */
  private final long expiration;

  private PingPacketData(
      final Optional<Endpoint> maybeFrom, final Endpoint to, final long expiration) {
    checkArgument(to != null, "destination endpoint cannot be null");
    checkArgument(expiration >= 0, "expiration cannot be negative");

    this.maybeFrom = maybeFrom;
    this.to = to;
    this.expiration = expiration;
  }

  public static PingPacketData create(final Endpoint from, final Endpoint to) {
    return create(from, to, PacketData.defaultExpiration());
  }

  static PingPacketData create(final Endpoint from, final Endpoint to, final long expirationSec) {
    return new PingPacketData(Optional.of(from), to, expirationSec);
  }

  public static PingPacketData readFrom(final RLPInput in) {
    in.enterList();
    // The first element signifies the "version", but this value is ignored as of EIP-8
    in.readBigIntegerScalar();
    Optional<Endpoint> from;
    try {
      from = Optional.of(Endpoint.decodeStandalone(in));
    } catch (RLPException __) {
      from = Optional.empty();
      // We don't care if
    }
    final Endpoint to = Endpoint.decodeStandalone(in);
    final long expiration = in.readLongScalar();
    in.leaveListLenient();
    return new PingPacketData(from, to, expiration);
  }

  @Override
  public void writeTo(final RLPOutput out) {
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

  @Override
  public String toString() {
    return "PingPacketData{"
        + "from="
        + maybeFrom.map(Object::toString).orElse("INVALID")
        + ", to="
        + to
        + ", expiration="
        + expiration
        + '}';
  }
}
