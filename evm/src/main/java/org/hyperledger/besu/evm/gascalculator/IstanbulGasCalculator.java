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

import org.hyperledger.besu.evm.account.Account;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class IstanbulGasCalculator extends PetersburgGasCalculator {

  private static final long TX_DATA_ZERO_COST = 4L;
  private static final long ISTANBUL_TX_DATA_NON_ZERO_COST = 16L;
  private static final long TX_BASE_COST = 21_000L;

  private static final long SLOAD_GAS = 800L;
  private static final long BALANCE_OPERATION_GAS_COST = 700L;
  private static final long EXTCODE_HASH_COST = 700L;

  private static final long SSTORE_SET_GAS = 20_000L;
  private static final long SSTORE_RESET_GAS = 5_000L;
  private static final long SSTORE_CLEARS_SCHEDULE = 15_000L;

  private static final long SSTORE_SET_GAS_LESS_SLOAD_GAS = SSTORE_SET_GAS - SLOAD_GAS;
  private static final long SSTORE_RESET_GAS_LESS_SLOAD_GAS = SSTORE_RESET_GAS - SLOAD_GAS;
  private static final long NEGATIVE_SSTORE_CLEARS_SCHEDULE = -SSTORE_CLEARS_SCHEDULE;

  @Override
  public long transactionIntrinsicGasCost(final Bytes payload, final boolean isContractCreation) {
    int zeros = 0;
    for (int i = 0; i < payload.size(); i++) {
      if (payload.get(i) == 0) {
        ++zeros;
      }
    }
    final int nonZeros = payload.size() - zeros;

    final long cost =
        TX_BASE_COST + (TX_DATA_ZERO_COST * zeros) + (ISTANBUL_TX_DATA_NON_ZERO_COST * nonZeros);

    return isContractCreation ? (cost + txCreateExtraGasCost()) : cost;
  }

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

  @Override
  public long getMaximumTransactionCost(final int size) {
    return TX_BASE_COST + (ISTANBUL_TX_DATA_NON_ZERO_COST * size);
  }
}
