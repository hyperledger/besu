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

import org.hyperledger.besu.crypto.altbn128.AltBn128Fq12Pairer;
import org.hyperledger.besu.crypto.altbn128.AltBn128Fq2Point;
import org.hyperledger.besu.crypto.altbn128.AltBn128Point;
import org.hyperledger.besu.crypto.altbn128.Fq;
import org.hyperledger.besu.crypto.altbn128.Fq12;
import org.hyperledger.besu.crypto.altbn128.Fq2;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AltBN128PairingPrecompiledContract extends AbstractPrecompiledContract {

  private static final int FIELD_LENGTH = 32;
  private static final int PARAMETER_LENGTH = 192;

  static final BytesValue FALSE =
      BytesValue.fromHexString(
          "0x0000000000000000000000000000000000000000000000000000000000000000");
  static final BytesValue TRUE =
      BytesValue.fromHexString(
          "0x0000000000000000000000000000000000000000000000000000000000000001");

  private final Gas pairingGasCost;
  private final Gas baseGasCost;

  private AltBN128PairingPrecompiledContract(
      final GasCalculator gasCalculator, final Gas pairingGasCost, final Gas baseGasCost) {
    super("AltBN128Pairing", gasCalculator);
    this.pairingGasCost = pairingGasCost;
    this.baseGasCost = baseGasCost;
  }

  public static AltBN128PairingPrecompiledContract byzantium(final GasCalculator gasCalculator) {
    return new AltBN128PairingPrecompiledContract(gasCalculator, Gas.of(80_000), Gas.of(100_000));
  }

  public static AltBN128PairingPrecompiledContract istanbul(final GasCalculator gasCalculator) {
    return new AltBN128PairingPrecompiledContract(gasCalculator, Gas.of(34_000), Gas.of(45_000));
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    final int parameters = input.size() / PARAMETER_LENGTH;
    return pairingGasCost.times(parameters).plus(baseGasCost);
  }

  @Override
  public BytesValue compute(final BytesValue input, final MessageFrame messageFrame) {
    if (input.isEmpty()) {
      return TRUE;
    }
    if (input.size() % PARAMETER_LENGTH != 0) {
      return null;
    }

    final int parameters = input.size() / PARAMETER_LENGTH;
    final List<AltBn128Point> a = new ArrayList<>();
    final List<AltBn128Fq2Point> b = new ArrayList<>();
    for (int i = 0; i < parameters; ++i) {
      final BigInteger p1_x = extractParameter(input, i * PARAMETER_LENGTH, FIELD_LENGTH);
      final BigInteger p1_y = extractParameter(input, i * PARAMETER_LENGTH + 32, FIELD_LENGTH);
      final AltBn128Point p1 = new AltBn128Point(Fq.create(p1_x), Fq.create(p1_y));
      if (!p1.isOnCurve()) {
        return null;
      }
      a.add(p1);

      final BigInteger p2_xImag = extractParameter(input, i * PARAMETER_LENGTH + 64, FIELD_LENGTH);
      final BigInteger p2_xReal = extractParameter(input, i * PARAMETER_LENGTH + 96, FIELD_LENGTH);
      final BigInteger p2_yImag = extractParameter(input, i * PARAMETER_LENGTH + 128, FIELD_LENGTH);
      final BigInteger p2_yReal = extractParameter(input, i * PARAMETER_LENGTH + 160, FIELD_LENGTH);
      final Fq2 p2_x = Fq2.create(p2_xReal, p2_xImag);
      final Fq2 p2_y = Fq2.create(p2_yReal, p2_yImag);
      final AltBn128Fq2Point p2 = new AltBn128Fq2Point(p2_x, p2_y);
      if (!p2.isOnCurve() || !p2.isInGroup()) {
        return null;
      }
      b.add(p2);
    }

    Fq12 exponent = Fq12.one();
    for (int i = 0; i < parameters; ++i) {
      exponent = exponent.multiply(AltBn128Fq12Pairer.pair(a.get(i), b.get(i)));
    }

    if (AltBn128Fq12Pairer.finalize(exponent).equals(Fq12.one())) {
      return TRUE;
    } else {
      return FALSE;
    }
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
