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

import java.time.Clock;

public class ENRRequestPacketData implements PacketData {
  /* In seconds after epoch. */
  private final long expiration;

  private ENRRequestPacketData(final long expiration) {
    this.expiration = expiration;
  }

  public static ENRRequestPacketData create() {
    return create(PacketData.defaultExpiration(), Clock.systemUTC());
  }

  static ENRRequestPacketData create(final long expirationSec, final Clock clock) {
    validateParameters(expirationSec, clock);
    return new ENRRequestPacketData(expirationSec);
  }

  public static ENRRequestPacketData readFrom(final RLPInput in, final Clock clock) {
    in.enterList();
    final long expiration = in.readLongScalar();
    in.leaveListLenient();
    validateParameters(expiration, clock);
    return new ENRRequestPacketData(expiration);
  }

  private static void validateParameters(final long expiration, final Clock clock) {
    checkArgument(expiration >= 0, "expiration cannot be negative");
    checkArgument(
        expiration >= clock.instant().getEpochSecond(), "expiration cannot be in the past");
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeLongScalar(expiration);
    out.endList();
  }

  public long getExpiration() {
    return expiration;
  }

  @Override
  public String toString() {
    return "ENRRequestPacketData{" + "expiration=" + expiration + '}';
  }
}
