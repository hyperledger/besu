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
 */
package org.hyperledger.besu.evm.gascalculator;

import static org.hyperledger.besu.datatypes.Address.BLAKE2B_F_COMPRESSION;
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;
import static org.hyperledger.besu.evm.internal.Words.clampedMultiply;
import static org.hyperledger.besu.evm.internal.Words.clampedToInt;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.precompile.BigIntegerModularExponentiationPrecompiledContract;

import java.math.BigInteger;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** The Berlin gas calculator. */
public class BerlinGasCalculator extends IstanbulGasCalculator {

  // new constants for EIP-2929
  private static final long COLD_SLOAD_COST = 2100L;
  private static final long COLD_ACCOUNT_ACCESS_COST = 2600L;

  /** Warm storage read, defined in EIP-2929 */
  protected static final long WARM_STORAGE_READ_COST = 100L;

  private static final long ACCESS_LIST_ADDRESS_COST = 2400L;

  /** The constant ACCESS_LIST_STORAGE_COST. */
  protected static final long ACCESS_LIST_STORAGE_COST = 1900L;

  // redefinitions for EIP-2929
  private static final long SLOAD_GAS = WARM_STORAGE_READ_COST;

  /** The constant SSTORE_RESET_GAS. */
  protected static final long SSTORE_RESET_GAS = 5000L - COLD_SLOAD_COST;

  // unchanged from Istanbul
  private static final long SSTORE_SET_GAS = 20_000L;
  private static final long SSTORE_CLEARS_SCHEDULE = 15_000L;

  /** The constant SSTORE_SET_GAS_LESS_SLOAD_GAS. */
  protected static final long SSTORE_SET_GAS_LESS_SLOAD_GAS = SSTORE_SET_GAS - SLOAD_GAS;

  /** The constant SSTORE_RESET_GAS_LESS_SLOAD_GAS. */
  protected static final long SSTORE_RESET_GAS_LESS_SLOAD_GAS = SSTORE_RESET_GAS - SLOAD_GAS;

  private static final long NEGATIVE_SSTORE_CLEARS_SCHEDULE = -SSTORE_CLEARS_SCHEDULE;

  // unchanged from Frontier
  private static final long COPY_WORD_GAS_COST = 3L;

  private final int maxPrecompile;

  /**
   * Instantiates a new Berlin gas calculator.
   *
   * @param maxPrecompile the max precompile
   */
  protected BerlinGasCalculator(final int maxPrecompile) {
    this.maxPrecompile = maxPrecompile;
  }

  /** Instantiates a new Berlin gas calculator. */
  public BerlinGasCalculator() {
    this(BLAKE2B_F_COMPRESSION.toArrayUnsafe()[19]);
  }

  @Override
  public long accessListGasCost(final int addresses, final int storageSlots) {
    return ACCESS_LIST_ADDRESS_COST * addresses + (ACCESS_LIST_STORAGE_COST * storageSlots);
  }

  @Override
  public boolean isPrecompile(final Address address) {
    final byte[] addressBytes = address.toArrayUnsafe();
    for (int i = 0; i < 19; i++) {
      if (addressBytes[i] != 0) {
        return false;
      }
    }
    final int lastByte = Byte.toUnsignedInt(addressBytes[19]);
    return lastByte <= maxPrecompile && lastByte != 0;
  }

  // new costs
  @Override
  public long getColdSloadCost() {
    return COLD_SLOAD_COST;
  }

  @Override
  public long getColdAccountAccessCost() {
    return COLD_ACCOUNT_ACCESS_COST;
  }

  @Override
  public long getWarmStorageReadCost() {
    return WARM_STORAGE_READ_COST;
  }

  // Zeroed out old costs
  @Override
  public long getBalanceOperationGasCost() {
    return 0L;
  }

  @Override
  public long callOperationBaseGasCost() {
    return 0L;
  }

  @Override
  public long extCodeHashOperationGasCost() {
    return 0L;
  }

  @Override
  public long getExtCodeSizeOperationGasCost() {
    return 0L;
  }

  @Override
  public long getSloadOperationGasCost() {
    return 0L;
  }

  // Redefined costs from EIP-2929
  @Override
  @SuppressWarnings("java:S5738")
  public long callOperationGasCost(
      final MessageFrame frame,
      final long stipend,
      final long inputDataOffset,
      final long inputDataLength,
      final long outputDataOffset,
      final long outputDataLength,
      final Wei transferValue,
      final Account recipient,
      final Address to) {
    return callOperationGasCost(
        frame,
        stipend,
        inputDataOffset,
        inputDataLength,
        outputDataOffset,
        outputDataLength,
        transferValue,
        recipient,
        to,
        frame.warmUpAddress(to) || isPrecompile(to));
  }

  // Redefined costs from EIP-2929
  @Override
  public long callOperationGasCost(
      final MessageFrame frame,
      final long stipend,
      final long inputDataOffset,
      final long inputDataLength,
      final long outputDataOffset,
      final long outputDataLength,
      final Wei transferValue,
      final Account recipient,
      final Address to,
      final boolean accountIsWarm) {
    final long baseCost =
        super.callOperationGasCost(
            frame,
            stipend,
            inputDataOffset,
            inputDataLength,
            outputDataOffset,
            outputDataLength,
            transferValue,
            recipient,
            to,
            true); // we want the "warmed price" as we will charge for warming ourselves
    return clampedAdd(
        baseCost, accountIsWarm ? getWarmStorageReadCost() : getColdAccountAccessCost());
  }

  // defined in Frontier, but re-implemented with no base cost.
  @Override
  public long extCodeCopyOperationGasCost(
      final MessageFrame frame, final long offset, final long length) {
    return copyWordsToMemoryGasCost(frame, 0L, COPY_WORD_GAS_COST, offset, length);
  }

  // defined in Istanbul, but re-implemented with new constants
  @Override
  // As per https://eips.ethereum.org/EIPS/eip-2200
  public long calculateStorageCost(
      final UInt256 newValue,
      final Supplier<UInt256> currentValue,
      final Supplier<UInt256> originalValue) {

    final UInt256 localCurrentValue = currentValue.get();
    if (localCurrentValue.equals(newValue)) {
      return SLOAD_GAS;
    } else {
      final UInt256 localOriginalValue = originalValue.get();
      if (localOriginalValue.equals(localCurrentValue)) {
        return localOriginalValue.isZero() ? SSTORE_SET_GAS : SSTORE_RESET_GAS;
      } else {
        return SLOAD_GAS;
      }
    }
  }

  // defined in Istanbul, but re-implemented with new constants
  @Override
  // As per https://eips.ethereum.org/EIPS/eip-2200
  public long calculateStorageRefundAmount(
      final UInt256 newValue,
      final Supplier<UInt256> currentValue,
      final Supplier<UInt256> originalValue) {

    final UInt256 localCurrentValue = currentValue.get();
    if (localCurrentValue.equals(newValue)) {
      return 0L;
    } else {
      final UInt256 localOriginalValue = originalValue.get();
      if (localOriginalValue.equals(localCurrentValue)) {
        if (localOriginalValue.isZero()) {
          return 0L;
        } else if (newValue.isZero()) {
          return SSTORE_CLEARS_SCHEDULE;
        } else {
          return 0L;
        }
      } else {
        long refund = 0L;
        if (!localOriginalValue.isZero()) {
          if (localCurrentValue.isZero()) {
            refund = NEGATIVE_SSTORE_CLEARS_SCHEDULE;
          } else if (newValue.isZero()) {
            refund = SSTORE_CLEARS_SCHEDULE;
          }
        }

        if (localOriginalValue.equals(newValue)) {
          refund =
              refund
                  + (localOriginalValue.isZero()
                      ? SSTORE_SET_GAS_LESS_SLOAD_GAS
                      : SSTORE_RESET_GAS_LESS_SLOAD_GAS);
        }
        return refund;
      }
    }
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

    long multiplicationComplexity = (Math.max(modulusLength, baseLength) + 7L) / 8L;
    multiplicationComplexity =
        Words.clampedMultiply(multiplicationComplexity, multiplicationComplexity);

    if (multiplicationComplexity == 0) {
      return 200;
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

    return Math.max(gasRequirement, 200L);
  }
}
