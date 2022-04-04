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

import org.apache.tuweni.units.bigints.UInt256;

public class LondonGasCalculator extends BerlinGasCalculator {

  // redefinitions for EIP-3529
  private static final long SSTORE_CLEARS_SCHEDULE = SSTORE_RESET_GAS + ACCESS_LIST_STORAGE_COST;

  private static final long NEGATIVE_SSTORE_CLEARS_SCHEDULE = -SSTORE_CLEARS_SCHEDULE;

  // redefinitions for EIP-3529
  private static final int NEW_MAX_REFUND_QUOTIENT = 5;

  public LondonGasCalculator() {}

  // Redefined refund amount from EIP-3529
  @Override
  public long getSelfDestructRefundAmount() {
    return 0L;
  }

  // defined in Berlin, but re-implemented with new constants
  @Override
  // As per https://eips.ethereum.org/EIPS/eip-3529
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
  public long getMaxRefundQuotient() {
    return NEW_MAX_REFUND_QUOTIENT;
  }
}
