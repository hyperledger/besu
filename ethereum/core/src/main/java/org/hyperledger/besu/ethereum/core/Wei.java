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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.uint.BaseUInt256Value;
import org.hyperledger.besu.util.uint.Counter;
import org.hyperledger.besu.util.uint.UInt256;

import java.math.BigInteger;

/** A particular quantity of Wei, the Ethereum currency. */
public final class Wei extends BaseUInt256Value<Wei> {

  public static final Wei ZERO = of(0);

  protected Wei(final Bytes32 bytes) {
    super(bytes, WeiCounter::new);
  }

  private Wei(final long v) {
    super(v, WeiCounter::new);
  }

  private Wei(final BigInteger v) {
    super(v, WeiCounter::new);
  }

  private Wei(final String hexString) {
    super(hexString, WeiCounter::new);
  }

  public static Wei of(final long value) {
    return new Wei(value);
  }

  public static Wei of(final BigInteger value) {
    return new Wei(value);
  }

  public static Wei of(final UInt256 value) {
    return new Wei(value.getBytes().copy());
  }

  public static Wei wrap(final Bytes32 value) {
    return new Wei(value);
  }

  public static Wei fromHexString(final String str) {
    return new Wei(str);
  }

  public static Wei fromEth(final long eth) {
    return Wei.of(BigInteger.valueOf(eth).multiply(BigInteger.TEN.pow(18)));
  }

  private static class WeiCounter extends Counter<Wei> {
    private WeiCounter() {
      super(Wei::new);
    }
  }
}
