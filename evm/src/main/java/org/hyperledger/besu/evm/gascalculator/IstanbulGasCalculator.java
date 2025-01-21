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

import static org.hyperledger.besu.evm.internal.Words.clampedAdd;

import java.util.function.Supplier;

import org.apache.tuweni.units.bigints.UInt256;

/** The Istanbul gas calculator. */
public class IstanbulGasCalculator extends PetersburgGasCalculator {

  private static final long TX_DATA_ZERO_COST = 4L;
  private static final long ISTANBUL_TX_DATA_NON_ZERO_COST = 16L;

  private static final long SLOAD_GAS = 800L;
  private static final long BALANCE_OPERATION_GAS_COST = 700L;
  private static final long EXTCODE_HASH_COST = 700L;

  private static final long SSTORE_SET_GAS = 20_000L;
  private static final long SSTORE_RESET_GAS = 5_000L;
  private static final long SSTORE_CLEARS_SCHEDULE = 15_000L;

  private static final long SSTORE_SET_GAS_LESS_SLOAD_GAS = SSTORE_SET_GAS - SLOAD_GAS;
  private static final long SSTORE_RESET_GAS_LESS_SLOAD_GAS = SSTORE_RESET_GAS - SLOAD_GAS;
  private static final long NEGATIVE_SSTORE_CLEARS_SCHEDULE = -SSTORE_CLEARS_SCHEDULE;

  /** Default constructor. */
  public IstanbulGasCalculator() {}

  @Override
  protected long callDataCost(final long payloadSize, final long zeroBytes) {
    return clampedAdd(
        ISTANBUL_TX_DATA_NON_ZERO_COST * (payloadSize - zeroBytes), TX_DATA_ZERO_COST * zeroBytes);
  }

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
  // As per https://eips.ethereum.org/EIPS/eip-1884
  public long getSloadOperationGasCost() {
    return SLOAD_GAS;
  }

  @Override
  // As per https://eips.ethereum.org/EIPS/eip-1884
  public long getBalanceOperationGasCost() {
    return BALANCE_OPERATION_GAS_COST;
  }

  @Override
  // As per https://eips.ethereum.org/EIPS/eip-1884
  public long extCodeHashOperationGasCost() {
    return EXTCODE_HASH_COST;
  }
}
