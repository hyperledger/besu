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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.precompile.BigIntegerModularExponentiationPrecompiledContract;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class BerlinGasCalculator extends IstanbulGasCalculator {

  // new constants for EIP-2929
  private static final Gas COLD_SLOAD_COST = Gas.of(2100);
  private static final Gas COLD_ACCOUNT_ACCESS_COST = Gas.of(2600);
  private static final Gas WARM_STORAGE_READ_COST = Gas.of(100);
  protected static final Gas ACCESS_LIST_STORAGE_COST = Gas.of(1900);

  // redefinitions for EIP-2929
  private static final Gas SLOAD_GAS = WARM_STORAGE_READ_COST;
  protected static final Gas SSTORE_RESET_GAS = Gas.of(5000L).minus(COLD_SLOAD_COST);

  // unchanged from Istanbul
  private static final Gas SSTORE_SET_GAS = Gas.of(20_000);
  private static final Gas SSTORE_CLEARS_SCHEDULE = Gas.of(15_000);

  protected static final Gas SSTORE_SET_GAS_LESS_SLOAD_GAS = SSTORE_SET_GAS.minus(SLOAD_GAS);
  protected static final Gas SSTORE_RESET_GAS_LESS_SLOAD_GAS = SSTORE_RESET_GAS.minus(SLOAD_GAS);
  private static final Gas NEGATIVE_SSTORE_CLEARS_SCHEDULE = Gas.ZERO.minus(SSTORE_CLEARS_SCHEDULE);

  // unchanged from Frontier
  private static final Gas COPY_WORD_GAS_COST = Gas.of(3L);

  private final int maxPrecompile;

  protected BerlinGasCalculator(final int maxPrecompile) {
    this.maxPrecompile = maxPrecompile;
  }

  public BerlinGasCalculator() {
    this(BLAKE2B_F_COMPRESSION.toArrayUnsafe()[19]);
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
  public Gas getColdSloadCost() {
    return COLD_SLOAD_COST;
  }

  @Override
  public Gas getColdAccountAccessCost() {
    return COLD_ACCOUNT_ACCESS_COST;
  }

  @Override
  public Gas getWarmStorageReadCost() {
    return WARM_STORAGE_READ_COST;
  }

  // Zeroed out old costs
  @Override
  public Gas getBalanceOperationGasCost() {
    return Gas.ZERO;
  }

  @Override
  public Gas callOperationBaseGasCost() {
    return Gas.ZERO;
  }

  @Override
  public Gas extCodeHashOperationGasCost() {
    return Gas.ZERO;
  }

  @Override
  public Gas getExtCodeSizeOperationGasCost() {
    return Gas.ZERO;
  }

  @Override
  public Gas getSloadOperationGasCost() {
    return Gas.ZERO;
  }

  // Redefined costs from EIP-2929
  @Override
  public Gas callOperationGasCost(
      final MessageFrame frame,
      final Gas stipend,
      final UInt256 inputDataOffset,
      final UInt256 inputDataLength,
      final UInt256 outputDataOffset,
      final UInt256 outputDataLength,
      final Wei transferValue,
      final Account recipient,
      final Address to) {
    final Gas baseCost =
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
    return baseCost.plus(accountIsWarm ? getWarmStorageReadCost() : getColdAccountAccessCost());
  }

  // defined in Frontier, but re-implemented with no base cost.
  @Override
  public Gas extCodeCopyOperationGasCost(
      final MessageFrame frame, final UInt256 offset, final UInt256 length) {
    return copyWordsToMemoryGasCost(frame, Gas.ZERO, COPY_WORD_GAS_COST, offset, length);
  }

  // defined in Istanbul, but re-implemented with new constants
  @Override
  // As per https://eips.ethereum.org/EIPS/eip-2200
  public Gas calculateStorageCost(
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
  public Gas calculateStorageRefundAmount(
      final Account account, final UInt256 key, final UInt256 newValue) {

    final UInt256 currentValue = account.getStorageValue(key);
    if (currentValue.equals(newValue)) {
      return Gas.ZERO;
    } else {
      final UInt256 originalValue = account.getOriginalStorageValue(key);
      if (originalValue.equals(currentValue)) {
        if (originalValue.isZero()) {
          return Gas.ZERO;
        } else if (newValue.isZero()) {
          return SSTORE_CLEARS_SCHEDULE;
        } else {
          return Gas.ZERO;
        }
      } else {
        Gas refund = Gas.ZERO;
        if (!originalValue.isZero()) {
          if (currentValue.isZero()) {
            refund = NEGATIVE_SSTORE_CLEARS_SCHEDULE;
          } else if (newValue.isZero()) {
            refund = SSTORE_CLEARS_SCHEDULE;
          }
        }

        if (originalValue.equals(newValue)) {
          refund =
              refund.plus(
                  originalValue.isZero()
                      ? SSTORE_SET_GAS_LESS_SLOAD_GAS
                      : SSTORE_RESET_GAS_LESS_SLOAD_GAS);
        }
        return refund;
      }
    }
  }

  @Override
  public Gas modExpGasCost(final Bytes input) {
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

    // Gas price is so large it will not fit in a Gas type, so an
    // very very very unlikely high gas price is used instead.
    if (gasRequirement.bitLength() > ByzantiumGasCalculator.MAX_GAS_BITS) {
      return Gas.of(Long.MAX_VALUE);
    } else {
      return Gas.of(gasRequirement.max(BigInteger.valueOf(200)));
    }
  }
}
