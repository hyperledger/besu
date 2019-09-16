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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.util.bytes.AbstractBytes32Backed;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.MutableBytes32;

/**
 * Default implementation of a {@link Int256}.
 *
 * <p>Note that this class is not meant to be exposed outside of this package. Use {@link Int256}
 * static methods to build {@link Int256} values instead.
 */
class DefaultInt256 extends AbstractBytes32Backed implements Int256 {

  DefaultInt256(final Bytes32 bytes) {
    super(bytes);
    checkArgument(
        bytes.size() == SIZE,
        "Invalid value for a UInt256: expecting %s bytes but got %s",
        SIZE,
        bytes.size());
  }

  // Note meant to be used directly, use Int256.MINUS_ONE instead
  static DefaultInt256 minusOne() {
    final MutableBytes32 v = MutableBytes32.create();
    v.fill((byte) 0xFF);
    return new DefaultInt256(v);
  }

  private Int256 binaryOp(final Int256 value, final UInt256Bytes.BinaryOp op) {
    final MutableBytes32 result = MutableBytes32.create();
    op.applyOp(getBytes(), value.getBytes(), result);
    return new DefaultInt256(result);
  }

  @Override
  public Int256 dividedBy(final Int256 value) {
    return binaryOp(value, Int256Bytes::divide);
  }

  @Override
  public Int256 mod(final Int256 value) {
    return binaryOp(value, Int256Bytes::mod);
  }

  @Override
  public int compareTo(final Int256 other) {
    final boolean thisNeg = this.isNegative();
    final boolean otherNeg = other.isNegative();

    if (thisNeg) {
      // We're negative, if the other isn't it is bigger, otherwise both negative => compare same as
      // unsigned.
      return otherNeg ? UInt256Bytes.compareUnsigned(getBytes(), other.getBytes()) : -1;
    }

    // We're positive, if the other isn't we are bigger, otherwise both are positive and we can use
    // unsigned comparison.
    return otherNeg ? 1 : UInt256Bytes.compareUnsigned(getBytes(), other.getBytes());
  }
}
