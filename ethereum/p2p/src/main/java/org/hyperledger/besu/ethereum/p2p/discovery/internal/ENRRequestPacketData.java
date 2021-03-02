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

public class ENRRequestPacketData implements PacketData {
  /* In seconds after epoch. */
  private final long expiration;

  private ENRRequestPacketData(final long expiration) {
    checkArgument(expiration >= 0, "expiration cannot be negative");

    this.expiration = expiration;
  }

  public static ENRRequestPacketData create() {
    return create(PacketData.defaultExpiration());
  }

  static ENRRequestPacketData create(final long expirationSec) {
    return new ENRRequestPacketData(expirationSec);
  }

  public static ENRRequestPacketData readFrom(final RLPInput in) {
    in.enterList();
    final long expiration = in.readLongScalar();
    in.leaveListLenient();
    return new ENRRequestPacketData(expiration);
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
