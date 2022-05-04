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
import static org.hyperledger.besu.evm.internal.Words.clampedMultiply;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class ConstantinopleGasCalculator extends ByzantiumGasCalculator {

  private static final long SSTORE_NO_OP_COST = 200L;
  private static final long SSTORE_ADDITIONAL_WRITE_COST = 200L;
  private static final long SSTORE_FIRST_DIRTY_NEW_STORAGE_COST = 20_000L;
  private static final long SSTORE_FIRST_DIRTY_EXISTING_STORAGE_COST = 5_000L;
  private static final long STORAGE_RESET_REFUND_AMOUNT = 15_000L;
  private static final long NEGATIVE_STORAGE_RESET_REFUND_AMOUNT = -STORAGE_RESET_REFUND_AMOUNT;
  private static final long SSTORE_DIRTY_RETURN_TO_UNUSED_REFUND_AMOUNT = 19800L;
  private static final long SSTORE_DIRTY_RETURN_TO_ORIGINAL_VALUE_REFUND_AMOUNT = 4800L;

  private static final long EXTCODE_HASH_COST = 400L;

  @Override
  public long create2OperationGasCost(final MessageFrame frame) {
    final long initCodeLength = clampedToLong(frame.getStackItem(2));
    final long numWords = clampedAdd(initCodeLength, 31) / Bytes32.SIZE;
    final long initCodeHashCost = clampedMultiply(KECCAK256_OPERATION_WORD_GAS_COST, numWords);
    return clampedAdd(createOperationGasCost(frame), initCodeHashCost);
  }

  @Override
  // As per https://eips.ethereum.org/EIPS/eip-1283
  public long calculateStorageCost(
      final Account account, final UInt256 key, final UInt256 newValue) {

    final UInt256 currentValue = account.getStorageValue(key);
    if (currentValue.equals(newValue)) {
      return SSTORE_NO_OP_COST;
    } else {
      final UInt256 originalValue = account.getOriginalStorageValue(key);
      if (originalValue.equals(currentValue)) {
        return originalValue.isZero()
            ? SSTORE_FIRST_DIRTY_NEW_STORAGE_COST
            : SSTORE_FIRST_DIRTY_EXISTING_STORAGE_COST;
      } else {
        return SSTORE_ADDITIONAL_WRITE_COST;
      }
    }
  }

  @Override
  // As per https://eips.ethereum.org/EIPS/eip-1283
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
          return STORAGE_RESET_REFUND_AMOUNT;
        } else {
          return 0L;
        }
      } else {
        long refund = 0L;
        if (!originalValue.isZero()) {
          if (currentValue.isZero()) {
            refund = NEGATIVE_STORAGE_RESET_REFUND_AMOUNT;
          } else if (newValue.isZero()) {
            refund = STORAGE_RESET_REFUND_AMOUNT;
          }
        }

        if (originalValue.equals(newValue)) {
          refund =
              refund
                  + (originalValue.isZero()
                      ? SSTORE_DIRTY_RETURN_TO_UNUSED_REFUND_AMOUNT
                      : SSTORE_DIRTY_RETURN_TO_ORIGINAL_VALUE_REFUND_AMOUNT);
        }
        return refund;
      }
    }
  }

  @Override
  public long extCodeHashOperationGasCost() {
    return EXTCODE_HASH_COST;
  }
}
