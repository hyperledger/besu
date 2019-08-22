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
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.MutableBytesValue;

import java.math.BigInteger;
import java.util.Arrays;

public class AltBN128MulPrecompiledContract extends AbstractPrecompiledContract {

  private static final BigInteger MAX_N =
      new BigInteger(
          "115792089237316195423570985008687907853269984665640564039457584007913129639935");

  private final Gas gasCost;

  private AltBN128MulPrecompiledContract(final GasCalculator gasCalculator, final Gas gasCost) {
    super("AltBN128Mul", gasCalculator);
    this.gasCost = gasCost;
  }

  public static AltBN128MulPrecompiledContract byzantium(final GasCalculator gasCalculator) {
    return new AltBN128MulPrecompiledContract(gasCalculator, Gas.of(40_000));
  }

  public static AltBN128MulPrecompiledContract istanbul(final GasCalculator gasCalculator) {
    return new AltBN128MulPrecompiledContract(gasCalculator, Gas.of(6_000));
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return gasCost;
  }

  @Override
  public BytesValue compute(final BytesValue input, final MessageFrame messageFrame) {
    final BigInteger x = extractParameter(input, 0, 32);
    final BigInteger y = extractParameter(input, 32, 32);
    final BigInteger n = extractParameter(input, 64, 32);

    final AltBn128Point p = new AltBn128Point(Fq.create(x), Fq.create(y));
    if (!p.isOnCurve() || n.compareTo(MAX_N) > 0) {
      return null;
    }
    final AltBn128Point product = p.multiply(n);

    final BytesValue xResult = product.getX().toBytesValue();
    final BytesValue yResult = product.getY().toBytesValue();
    final MutableBytesValue result = MutableBytesValue.create(64);
    xResult.copyTo(result, 32 - xResult.size());
    yResult.copyTo(result, 64 - yResult.size());

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
