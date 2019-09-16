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
package org.hyperledger.besu.util.uint;

import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.Bytes32Backed;

/** A signed 256-bits precision number. */
/*
 * Implementation note: this interface is currently extremely bar-bones and contains only the
 * operations that are currently needed on signed numbers by the Ethereum VM code. We could (and
 * probably should) extend this with more operations to make this class more reusable.
 */
public interface Int256 extends Bytes32Backed, Comparable<Int256> {

  int SIZE = 32;

  /** The value -1. */
  Int256 MINUS_ONE = DefaultInt256.minusOne();

  static Int256 wrap(final Bytes32 bytes) {
    return new DefaultInt256(bytes);
  }

  default boolean isZero() {
    return getBytes().isZero();
  }

  /** @return True if the value is negative. */
  default boolean isNegative() {
    return getBytes().get(0) < 0;
  }

  Int256 dividedBy(Int256 value);

  Int256 mod(Int256 value);

  /** @return A view of the bytes of this number as signed (two's complement). */
  default UInt256 asUnsigned() {
    return new DefaultUInt256(getBytes());
  }
}
