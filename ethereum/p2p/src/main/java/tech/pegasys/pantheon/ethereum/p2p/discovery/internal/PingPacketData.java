/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.p2p.discovery.internal;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.p2p.discovery.Endpoint;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

public class PingPacketData implements PacketData {

  /* Fixed value that represents we're using v4 of the P2P discovery protocol. */
  private static final int VERSION = 4;

  /* Source. */
  private final Endpoint from;

  /* Destination. */
  private final Endpoint to;

  /* In millis after epoch. */
  private final long expiration;

  private PingPacketData(final Endpoint from, final Endpoint to, final long expiration) {
    checkArgument(from != null, "source endpoint cannot be null");
    checkArgument(to != null, "destination endpoint cannot be null");
    checkArgument(expiration >= 0, "expiration cannot be negative");

    this.from = from;
    this.to = to;
    this.expiration = expiration;
  }

  public static PingPacketData create(final Endpoint from, final Endpoint to) {
    return new PingPacketData(
        from, to, System.currentTimeMillis() + PacketData.DEFAULT_EXPIRATION_PERIOD_MS);
  }

  public static PingPacketData readFrom(final RLPInput in) {
    in.enterList();
    // The first element signifies the "version", but this value is ignored as of EIP-8
    in.readBigIntegerScalar();
    final Endpoint from = Endpoint.decodeStandalone(in);
    final Endpoint to = Endpoint.decodeStandalone(in);
    final long expiration = in.readLongScalar();
    in.leaveListLenient();
    return new PingPacketData(from, to, expiration);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeIntScalar(VERSION);
    from.encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(expiration);
    out.endList();
  }

  public Endpoint getFrom() {
    return from;
  }

  public Endpoint getTo() {
    return to;
  }

  public long getExpiration() {
    return expiration;
  }

  @Override
  public String toString() {
    return "PingPacketData{" + "from=" + from + ", to=" + to + ", expiration=" + expiration + '}';
  }
}
