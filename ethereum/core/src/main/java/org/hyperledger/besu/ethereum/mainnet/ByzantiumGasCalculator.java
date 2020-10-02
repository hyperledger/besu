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
 *
 */

package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BigIntegerModularExponentiationPrecompiledContract;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;

public class ByzantiumGasCalculator extends SpuriousDragonGasCalculator {

  @Override
  public Gas modExpGasCost(Bytes input) {
    // Typically gas calculations are delegated to a GasCalculator instance,
    // but the complexity and coupling with other parts of the precompile seem
    // like reasonable reasons to do the math here instead.
    final BigInteger baseLength = BigIntegerModularExponentiationPrecompiledContract.baseLength(input);
    final BigInteger exponentLength = BigIntegerModularExponentiationPrecompiledContract.exponentLength(input);
    final BigInteger modulusLength = BigIntegerModularExponentiationPrecompiledContract.modulusLength(input);
    final BigInteger exponentOffset = BigIntegerModularExponentiationPrecompiledContract.BASE_OFFSET.add(baseLength);
    final int firstExponentBytesCap = exponentLength.min(BigIntegerModularExponentiationPrecompiledContract.MAX_FIRST_EXPONENT_BYTES).intValue();
    final BigInteger firstExpBytes = BigIntegerModularExponentiationPrecompiledContract.extractParameter(input, exponentOffset, firstExponentBytesCap);
    final BigInteger adjustedExponentLength = BigIntegerModularExponentiationPrecompiledContract.adjustedExponentLength(exponentLength, firstExpBytes);
    final BigInteger multiplicationComplexity =
        BigIntegerModularExponentiationPrecompiledContract.multiplicationComplexity(baseLength.max(modulusLength));
    final BigInteger gasRequirement =
        multiplicationComplexity
            .multiply(adjustedExponentLength.max(BigInteger.ONE))
            .divide(BigIntegerModularExponentiationPrecompiledContract.GQUADDIVISOR);

    // Gas price is so large it will not fit in a Gas type, so an
    // very very very unlikely high gas price is used instead.
    if (gasRequirement.bitLength() > BigIntegerModularExponentiationPrecompiledContract.MAX_GAS_BITS) {
      return Gas.of(Long.MAX_VALUE);
    } else {
      return Gas.of(gasRequirement);
    }
  }
}
