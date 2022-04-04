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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.BigIntegerModularExponentiationPrecompiledContract;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class BerlinGasCalculator extends IstanbulGasCalculator {

  // new constants for EIP-2929
  private static final long COLD_SLOAD_COST = 2100L;
  private static final long COLD_ACCOUNT_ACCESS_COST = 2600L;
  private static final long WARM_STORAGE_READ_COST = 100L;
  private static final long ACCESS_LIST_ADDRESS_COST = 2400L;
  protected static final long ACCESS_LIST_STORAGE_COST = 1900L;

  // redefinitions for EIP-2929
  private static final long SLOAD_GAS = WARM_STORAGE_READ_COST;
  protected static final long SSTORE_RESET_GAS = 5000L - COLD_SLOAD_COST;

  // unchanged from Istanbul
  private static final long SSTORE_SET_GAS = 20_000L;
  private static final long SSTORE_CLEARS_SCHEDULE = 15_000L;

  protected static final long SSTORE_SET_GAS_LESS_SLOAD_GAS = SSTORE_SET_GAS - SLOAD_GAS;
  protected static final long SSTORE_RESET_GAS_LESS_SLOAD_GAS = SSTORE_RESET_GAS - SLOAD_GAS;
  private static final long NEGATIVE_SSTORE_CLEARS_SCHEDULE = -SSTORE_CLEARS_SCHEDULE;

  // unchanged from Frontier
  private static final long COPY_WORD_GAS_COST = 3L;

  private final int maxPrecompile;

  protected BerlinGasCalculator(final int maxPrecompile) {
    this.maxPrecompile = maxPrecompile;
  }

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
            to);
    final boolean accountIsWarm = frame.warmUpAddress(to) || isPrecompile(to);
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
      final Account account, final UInt256 key, final UInt256 newValue) {

    final UInt256 currentValue = account.getStorageValue(key);
    if (currentValue.equals(newValue)) {
      return SLOAD_GAS;
    } else {
      final UInt256 originalValue = account.getOriginalStorageValue(key);
      if (originalValue.equals(currentValue)) {
        return originalValue.isZero() ? SSTORE_SET_GAS : SSTORE_RESET_GAS;
      } else {
        return SLOAD_GAS;
      }
    }
  }

  // defined in Istanbul, but re-implemented with new constants
  @Override
  // As per https://eips.ethereum.org/EIPS/eip-2200
  public long calculateStorageRefundAmount(
      final Account account, final UInt256 key, final UInt256 newValue) {

    final UInt256 currentValue = account.getStorageValue(key);
    if (currentValue.equals(newValue)) {
      return 0L;
    } else {
      final UInt256 originalValue = account.getOriginalStorageValue(key);
      if (originalValue.equals(currentValue)) {
        if (originalValue.isZero()) {
          return 0L;
        } else if (newValue.isZero()) {
          return SSTORE_CLEARS_SCHEDULE;
        } else {
          return 0L;
        }
      } else {
        long refund = 0L;
        if (!originalValue.isZero()) {
          if (currentValue.isZero()) {
            refund = NEGATIVE_SSTORE_CLEARS_SCHEDULE;
          } else if (newValue.isZero()) {
            refund = SSTORE_CLEARS_SCHEDULE;
          }
        }

        if (originalValue.equals(newValue)) {
          refund =
              refund
                  + (originalValue.isZero()
                      ? SSTORE_SET_GAS_LESS_SLOAD_GAS
                      : SSTORE_RESET_GAS_LESS_SLOAD_GAS);
        }
        return refund;
      }
    }
  }

  @Override
  public long modExpGasCost(final Bytes input) {
    final BigInteger baseLength =
        BigIntegerModularExponentiationPrecompiledContract.baseLength(input);
    final BigInteger exponentLength =
        BigIntegerModularExponentiationPrecompiledContract.exponentLength(input);
    final BigInteger modulusLength =
        BigIntegerModularExponentiationPrecompiledContract.modulusLength(input);
    final BigInteger exponentOffset =
        BigIntegerModularExponentiationPrecompiledContract.BASE_OFFSET.add(baseLength);
    final int firstExponentBytesCap =
        exponentLength.min(ByzantiumGasCalculator.MAX_FIRST_EXPONENT_BYTES).intValue();
    final BigInteger firstExpBytes =
        BigIntegerModularExponentiationPrecompiledContract.extractParameter(
            input, exponentOffset, firstExponentBytesCap);
    final BigInteger adjustedExponentLength = adjustedExponentLength(exponentLength, firstExpBytes);
    final BigInteger multiplicationComplexity =
        modulusLength
            .max(baseLength)
            .add(BigInteger.valueOf(7))
            .divide(BigInteger.valueOf(8))
            .pow(2);

    final BigInteger gasRequirement =
        multiplicationComplexity
            .multiply(adjustedExponentLength.max(BigInteger.ONE))
            .divide(BigInteger.valueOf(3));

    // Gas price is so large it will not fit in a Gas type, so a
    // very very very unlikely high gas price is used instead.
    if (gasRequirement.bitLength() > ByzantiumGasCalculator.MAX_GAS_BITS) {
      return Long.MAX_VALUE;
    } else {
      return Math.max(gasRequirement.longValueExact(), 200L);
    }
  }
}
