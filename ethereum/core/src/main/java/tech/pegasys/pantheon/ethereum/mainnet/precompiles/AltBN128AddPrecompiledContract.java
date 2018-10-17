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
package tech.pegasys.pantheon.ethereum.mainnet.precompiles;

import tech.pegasys.pantheon.crypto.altbn128.AltBn128Point;
import tech.pegasys.pantheon.crypto.altbn128.Fq;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.mainnet.AbstractPrecompiledContract;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.MutableBytesValue;

import java.math.BigInteger;
import java.util.Arrays;

public class AltBN128AddPrecompiledContract extends AbstractPrecompiledContract {

  public AltBN128AddPrecompiledContract(final GasCalculator gasCalculator) {
    super("AltBN128Add", gasCalculator);
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return Gas.of(500);
  }

  @Override
  public BytesValue compute(final BytesValue input) {
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
