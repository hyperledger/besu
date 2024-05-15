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
import static org.hyperledger.besu.evm.internal.Words.clampedToInt;

import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.function.Supplier;

import org.apache.tuweni.units.bigints.UInt256;

/** The Constantinople gas calculator. */
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

  /** Default constructor. */
  public ConstantinopleGasCalculator() {}

  /**
   * Returns the amount of gas the CREATE2 operation will consume.
   *
   * @param frame The current frame
   * @return the amount of gas the CREATE2 operation will consume
   * @deprecated Compose the operation cost from {@link #txCreateCost()}, {@link
   *     #memoryExpansionGasCost(MessageFrame, long, long)}, {@link #createKeccakCost(int)}, and
   *     {@link #initcodeCost(int)}. As done in {@link
   *     org.hyperledger.besu.evm.operation.Create2Operation#cost(MessageFrame, Supplier)}
   */
  @SuppressWarnings("removal")
  @Override
  @Deprecated(since = "24.4.1", forRemoval = true)
  public long create2OperationGasCost(final MessageFrame frame) {
    final int inputOffset = clampedToInt(frame.getStackItem(1));
    final int inputSize = clampedToInt(frame.getStackItem(2));
    return clampedAdd(
        clampedAdd(txCreateCost(), memoryExpansionGasCost(frame, inputOffset, inputSize)),
        clampedAdd(createKeccakCost(inputSize), initcodeCost(inputSize)));
  }

  @Override
  // As per https://eips.ethereum.org/EIPS/eip-1283
  public long calculateStorageCost(
      final UInt256 newValue,
      final Supplier<UInt256> currentValue,
      final Supplier<UInt256> originalValue) {

    final UInt256 localCurrentValue = currentValue.get();
    if (localCurrentValue.equals(newValue)) {
      return SSTORE_NO_OP_COST;
    } else {
      final UInt256 localOriginalValue = originalValue.get();
      if (localOriginalValue.equals(localCurrentValue)) {
        return localOriginalValue.isZero()
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
      final UInt256 newValue,
      final Supplier<UInt256> currentValue,
      final Supplier<UInt256> originalValue) {

    UInt256 localCurrentValue = currentValue.get();
    if (localCurrentValue.equals(newValue)) {
      return 0L;
    } else {
      UInt256 localOriginalValue = originalValue.get();
      if (localOriginalValue.equals(localCurrentValue)) {
        if (localOriginalValue.isZero()) {
          return 0L;
        } else if (newValue.isZero()) {
          return STORAGE_RESET_REFUND_AMOUNT;
        } else {
          return 0L;
        }
      } else {
        long refund = 0L;
        if (!localOriginalValue.isZero()) {
          if (localCurrentValue.isZero()) {
            refund = NEGATIVE_STORAGE_RESET_REFUND_AMOUNT;
          } else if (newValue.isZero()) {
            refund = STORAGE_RESET_REFUND_AMOUNT;
          }
        }

        if (localOriginalValue.equals(newValue)) {
          refund =
              refund
                  + (localOriginalValue.isZero()
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
