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
package org.hyperledger.besu.ethereum.mainnet.precompiles;

import org.hyperledger.besu.crypto.altbn128.AltBn128Point;
import org.hyperledger.besu.crypto.altbn128.Fq;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.MutableBytesValue;

import java.math.BigInteger;
import java.util.Arrays;

public class AltBN128AddPrecompiledContract extends AbstractPrecompiledContract {

  private final Gas gasCost;

  private AltBN128AddPrecompiledContract(final GasCalculator gasCalculator, final Gas gasCost) {
    super("AltBN128Add", gasCalculator);
    this.gasCost = gasCost;
  }

  public static AltBN128AddPrecompiledContract byzantium(final GasCalculator gasCalculator) {
    return new AltBN128AddPrecompiledContract(gasCalculator, Gas.of(500));
  }

  public static AltBN128AddPrecompiledContract istanbul(final GasCalculator gasCalculator) {
    return new AltBN128AddPrecompiledContract(gasCalculator, Gas.of(150));
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return gasCost;
  }

  @Override
  public BytesValue compute(final BytesValue input, final MessageFrame messageFrame) {
    final BigInteger x1 = extractParameter(input, 0, 32);
    final BigInteger y1 = extractParameter(input, 32, 32);
    final BigInteger x2 = extractParameter(input, 64, 32);
    final BigInteger y2 = extractParameter(input, 96, 32);

    final AltBn128Point p1 = new AltBn128Point(Fq.create(x1), Fq.create(y1));
    final AltBn128Point p2 = new AltBn128Point(Fq.create(x2), Fq.create(y2));
    if (!p1.isOnCurve() || !p2.isOnCurve()) {
      return null;
    }
    final AltBn128Point sum = p1.add(p2);
    final BytesValue x = sum.getX().toBytesValue();
    final BytesValue y = sum.getY().toBytesValue();
    final MutableBytesValue result = MutableBytesValue.create(64);
    x.copyTo(result, 32 - x.size());
    y.copyTo(result, 64 - y.size());

    return result;
  }

  private static BigInteger extractParameter(
      final BytesValue input, final int offset, final int length) {
    if (offset > input.size() || length == 0) {
      return BigInteger.ZERO;
    }
    final byte[] raw = Arrays.copyOfRange(input.extractArray(), offset, offset + length);
    return new BigInteger(1, raw);
  }
}
