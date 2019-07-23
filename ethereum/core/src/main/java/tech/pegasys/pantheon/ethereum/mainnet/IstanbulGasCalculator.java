/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

public class IstanbulGasCalculator extends ConstantinopleFixGasCalculator {

  private static final Gas TX_DATA_ZERO_COST = Gas.of(4L);
  private static final Gas TX_DATA_NON_ZERO_COST = Gas.of(16L);
  private static final Gas TX_BASE_COST = Gas.of(21_000L);

  private static final Gas SLOAD_GAS = Gas.of(800);
  private static final Gas SSTORE_SET_GAS = Gas.of(20_000);
  private static final Gas SSTORE_RESET_GAS = Gas.of(5_000);
  private static final Gas SSTORE_CLEARS_SCHEDULE = Gas.of(15_000);

  private static final Gas SSTORE_SET_GAS_LESS_SLOAD_GAS = SSTORE_SET_GAS.minus(SLOAD_GAS);
  private static final Gas SSTORE_RESET_GAS_LESS_SLOAD_GAS = SSTORE_RESET_GAS.minus(SLOAD_GAS);
  private static final Gas NEGATIVE_SSTORE_CLEARS_SCHEDULE = Gas.ZERO.minus(SSTORE_CLEARS_SCHEDULE);

  @Override
  public Gas transactionIntrinsicGasCost(final Transaction transaction) {
    final BytesValue payload = transaction.getPayload();
    int zeros = 0;
    for (int i = 0; i < payload.size(); i++) {
      if (payload.get(i) == 0) {
        ++zeros;
      }
    }
    final int nonZeros = payload.size() - zeros;

    Gas cost =
        Gas.ZERO
            .plus(TX_BASE_COST)
            .plus(TX_DATA_ZERO_COST.times(zeros))
            .plus(TX_DATA_NON_ZERO_COST.times(nonZeros));

    if (transaction.isContractCreation()) {
      cost = cost.plus(txCreateExtraGasCost());
    }

    return cost;
  }

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
}
