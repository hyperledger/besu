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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class TangerineWhistleGasCalculator extends HomesteadGasCalculator {

  private static final long BALANCE_OPERATION_GAS_COST = 400L;

  private static final long CALL_OPERATION_BASE_GAS_COST = 700L;

  private static final long EXT_CODE_BASE_GAS_COST = 700L;

  private static final long SELFDESTRUCT_OPERATION_GAS_COST = 5_000L;

  private static final long SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT = 30_000L;

  private static final long SLOAD_OPERATION_GAS_COST = 200L;

  @Override
  public long getBalanceOperationGasCost() {
    return BALANCE_OPERATION_GAS_COST;
  }

  // Returns all but 1/64 (n - floor(n /16)) of the provided value
  private static long allButOneSixtyFourth(final long value) {
    return value - value / 64;
  }

  @Override
  public long callOperationBaseGasCost() {
    return CALL_OPERATION_BASE_GAS_COST;
  }

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
    final long inputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, inputDataOffset, inputDataLength);
    final long outputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, outputDataOffset, outputDataLength);
    final long memoryExpansionCost =
        Math.max(inputDataMemoryExpansionCost, outputDataMemoryExpansionCost);

    long cost = clampedAdd(callOperationBaseGasCost(), memoryExpansionCost);

    if (!transferValue.isZero()) {
      cost = clampedAdd(cost, callValueTransferGasCost());
    }

    if (recipient == null) {
      cost = clampedAdd(cost, newAccountGasCost());
    }

    return cost;
  }

  private static long gasCap(final long remaining, final long stipend) {
    return Math.min(allButOneSixtyFourth(remaining), stipend);
  }

  @Override
  public long gasAvailableForChildCall(
      final MessageFrame frame, final long stipend, final boolean transfersValue) {
    final long gasCap = gasCap(frame.getRemainingGas(), stipend);

    // TODO: Integrate this into AbstractCallOperation since it's
    // a little out of place to mutate the frame here.
    frame.decrementRemainingGas(gasCap);

    if (transfersValue) {
      return gasCap + getAdditionalCallStipend();
    } else {
      return gasCap;
    }
  }

  @Override
  public long gasAvailableForChildCreate(final long stipend) {
    return allButOneSixtyFourth(stipend);
  }

  @Override
  protected long extCodeBaseGasCost() {
    return EXT_CODE_BASE_GAS_COST;
  }

  @Override
  public long selfDestructOperationGasCost(final Account recipient, final Wei inheritance) {
    if (recipient == null) {
      return SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT;
    } else {
      return SELFDESTRUCT_OPERATION_GAS_COST;
    }
  }

  @Override
  public long getSloadOperationGasCost() {
    return SLOAD_OPERATION_GAS_COST;
  }
}
