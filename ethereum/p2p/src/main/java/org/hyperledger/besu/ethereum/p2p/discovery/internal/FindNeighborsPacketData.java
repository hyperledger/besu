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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

public class FindNeighborsPacketData implements PacketData {
  private static final int TARGET_SIZE = 64;

  /* Node ID. */
  private final BytesValue target;

  /* In millis after epoch. */
  private final long expiration;

  private FindNeighborsPacketData(final BytesValue target, final long expiration) {
    checkArgument(target != null && target.size() == TARGET_SIZE, "target must be a valid node id");
    checkArgument(expiration >= 0, "expiration must be positive");

    this.target = target;
    this.expiration = expiration;
  }

  public static FindNeighborsPacketData create(final BytesValue target) {
    return new FindNeighborsPacketData(
        target, System.currentTimeMillis() + PacketData.DEFAULT_EXPIRATION_PERIOD_MS);
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();
    out.writeBytesValue(target);
    out.writeLongScalar(expiration);
    out.endList();
  }

  public static FindNeighborsPacketData readFrom(final RLPInput in) {
    in.enterList();
    final BytesValue target = in.readBytesValue();
    final long expiration = in.readLongScalar();
    in.leaveListLenient();
    return new FindNeighborsPacketData(target, expiration);
  }

  public long getExpiration() {
    return expiration;
  }

  public BytesValue getTarget() {
    return target;
  }

  @Override
  public String toString() {
    return "FindNeighborsPacketData{" + "expiration=" + expiration + ", target=" + target + '}';
  }
}
