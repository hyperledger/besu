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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Gas;

import org.apache.tuweni.units.bigints.UInt256;

public class LondonGasCalculator extends BerlinGasCalculator {

  // redefinitions for EIP-3529
  private static final Gas SSTORE_CLEARS_SCHEDULE = SSTORE_RESET_GAS.plus(ACCESS_LIST_STORAGE_COST);

  private static final Gas NEGATIVE_SSTORE_CLEARS_SCHEDULE = Gas.ZERO.minus(SSTORE_CLEARS_SCHEDULE);

  protected LondonGasCalculator() {}

  // Redefined refund amount from EIP-3529
  @Override
  public Gas getSelfDestructRefundAmount() {
    return Gas.ZERO;
  }

  // defined in Berlin, but re-implemented with new constants
  @Override
  // As per https://eips.ethereum.org/EIPS/eip-3529
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
}
