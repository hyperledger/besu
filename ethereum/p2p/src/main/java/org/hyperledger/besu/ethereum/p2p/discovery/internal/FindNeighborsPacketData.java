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

import org.apache.tuweni.bytes.Bytes;

public class FindNeighborsPacketData implements PacketData {
  private static final int TARGET_SIZE = 64;

  /* Node ID. */
  private final Bytes target;

  /* In seconds after epoch. */
  private final long expiration;

  private FindNeighborsPacketData(final Bytes target, final long expiration) {
    checkArgument(target != null && target.size() == TARGET_SIZE, "target must be a valid node id");
    checkArgument(expiration >= 0, "expiration must be positive");

    this.target = target;
    this.expiration = expiration;
  }

  public static FindNeighborsPacketData create(final Bytes target) {
    return create(target, PacketData.defaultExpiration());
  }

  static FindNeighborsPacketData create(final Bytes target, final long expirationSec) {
    return new FindNeighborsPacketData(target, expirationSec);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeBytes(target);
    out.writeLongScalar(expiration);
    out.endList();
  }

  public static FindNeighborsPacketData readFrom(final RLPInput in) {
    in.enterList();
    final Bytes target = in.readBytes();
    final long expiration = in.readLongScalar();
    in.leaveListLenient();
    return new FindNeighborsPacketData(target, expiration);
  }

  public long getExpiration() {
    return expiration;
  }

  public Bytes getTarget() {
    return target;
  }

  @Override
  public String toString() {
    return "FindNeighborsPacketData{" + "expiration=" + expiration + ", target=" + target + '}';
  }
}
