/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.gascalculator;

import static org.hyperledger.besu.datatypes.Address.BLS12_MAP_FP2_TO_G2;
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;
import static org.hyperledger.besu.evm.internal.Words.clampedMultiply;
import static org.hyperledger.besu.evm.internal.Words.clampedToInt;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.precompile.BigIntegerModularExponentiationPrecompiledContract;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;

/**
 * Gas Calculator for Osaka
 *
 * <UL>
 *   <LI>EIP-7883: ModExp Gas Cost Increase
 *   <LI>EIP-7907: Meter Contract Code Size And Increase Limit
 * </UL>
 */
public class OsakaGasCalculator extends PragueGasCalculator {
  /** The constant MAX_CODE_SIZE_WITH_NO_ACCESS_COST. */
  private static final int MAX_CODE_SIZE_WITH_NO_ACCESS_COST = 0x6000; // 24KB

  /** Instantiates a new Osaka Gas Calculator. */
  public OsakaGasCalculator() {
    this(BLS12_MAP_FP2_TO_G2.toArrayUnsafe()[19]);
  }

  /**
   * Instantiates a new Osaka Gas Calculator
   *
   * @param maxPrecompile the max precompile
   */
  protected OsakaGasCalculator(final int maxPrecompile) {
    super(maxPrecompile);
  }

  @Override
  public long modExpGasCost(final Bytes input) {
    final long baseLength = BigIntegerModularExponentiationPrecompiledContract.baseLength(input);
    final long exponentLength =
        BigIntegerModularExponentiationPrecompiledContract.exponentLength(input);
    final long modulusLength =
        BigIntegerModularExponentiationPrecompiledContract.modulusLength(input);
    final long exponentOffset =
        clampedAdd(BigIntegerModularExponentiationPrecompiledContract.BASE_OFFSET, baseLength);

    final long maxLength = Math.max(modulusLength, baseLength);
    long multiplicationComplexity = (maxLength + 7L) / 8L;
    multiplicationComplexity =
        Words.clampedMultiply(multiplicationComplexity, multiplicationComplexity);
    if (maxLength > 32) {
      multiplicationComplexity *= 2;
    }

    if (multiplicationComplexity == 0) {
      return 500L;
    } else if (multiplicationComplexity > 0) {
      long maxExponentLength = Long.MAX_VALUE / multiplicationComplexity * 3 / 8;
      if (exponentLength > maxExponentLength) {
        return Long.MAX_VALUE;
      }
    }

    final long firstExponentBytesCap =
        Math.min(exponentLength, ByzantiumGasCalculator.MAX_FIRST_EXPONENT_BYTES);
    final BigInteger firstExpBytes =
        BigIntegerModularExponentiationPrecompiledContract.extractParameter(
            input, clampedToInt(exponentOffset), clampedToInt(firstExponentBytesCap));
    final long adjustedExponentLength = adjustedExponentLength(exponentLength, firstExpBytes);

    long gasRequirement =
        clampedMultiply(multiplicationComplexity, Math.max(adjustedExponentLength, 1L));
    if (gasRequirement != Long.MAX_VALUE) {
      gasRequirement /= 3;
    }

    return Math.max(gasRequirement, 500L);
  }

  /**
   * Adjusted exponent length.
   *
   * @param exponentLength the exponent length
   * @param firstExpBytes the first exp bytes
   * @return the long
   */
  public static long adjustedExponentLength(
      final long exponentLength, final BigInteger firstExpBytes) {
    final int bitLength = bitLength(firstExpBytes);
    if (exponentLength <= WORD_SIZE) {
      return bitLength;
    } else {
      return clampedAdd(clampedMultiply(16, (exponentLength - WORD_SIZE)), bitLength);
    }
  }

  /**
   * Computes the gas cost for cold access to large contract code, as defined in EIP-7907.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Returns 0 if the code is already warm, or if the account has no code
   *   <li>Marks the code as warm if cold
   *   <li>Applies additional gas cost if code size exceeds 24KB
   * </ul>
   *
   * @param frame the current execution frame
   * @param account the target account
   * @return additional gas cost, or 0 if no cost applies
   */
  @Override
  public long calculateLargeCodeAccessCost(final MessageFrame frame, final Account account) {
    if (account == null) {
      return 0;
    }
    final Hash codeHash = account.getCodeHash();
    if (codeHash == null || codeHash.equals(Hash.EMPTY)) {
      return 0;
    }
    boolean isCodeWarm = frame.warmUpCode(account.getAddress());
    if (isCodeWarm) {
      return 0L;
    }
    return calculateLargeCodeSizeCost(account.getCodeSize());
  }

  /**
   * Compute the gas cost for delegated code resolution.
   *
   * <p>Per EIP-7907, this includes:
   *
   * <ul>
   *   <li>Standard access cost inherited from Prague calculator
   *   <li>Additional cost if the target has cold, large code
   * </ul>
   *
   * @param frame the current message frame
   * @param targetAccount the account being delegated to
   * @return total gas cost for code delegation resolution
   */
  @Override
  public long calculateCodeDelegationResolutionGas(
      final MessageFrame frame, final Account targetAccount) {
    long accessCost = super.calculateCodeDelegationResolutionGas(frame, targetAccount);
    return clampedAdd(accessCost, calculateLargeCodeAccessCost(frame, targetAccount));
  }

  /**
   * Calculates the gas cost for accessing code based on its size.
   *
   * <p>Contracts with code sizes exceeding 24KB are charged proportionally to the number of excess
   * 32-byte words, based on {@code INIT_CODE_COST}. Code sizes under or equal to the threshold
   * incur no extra cost.
   *
   * @param codeSize the size of the code in bytes
   * @return the calculated code access cost
   */
  private long calculateLargeCodeSizeCost(final long codeSize) {
    long excessCodeSize = Math.max(codeSize - MAX_CODE_SIZE_WITH_NO_ACCESS_COST, 0L);
    if (excessCodeSize == 0L) {
      return 0L;
    }
    return INIT_CODE_COST * Words.numWords(clampedToInt(excessCodeSize));
  }
}
