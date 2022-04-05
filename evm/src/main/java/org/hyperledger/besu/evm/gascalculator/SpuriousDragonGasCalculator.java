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

public class SpuriousDragonGasCalculator extends TangerineWhistleGasCalculator {

  private static final long EXP_OPERATION_BYTE_GAS_COST = 50L;

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

    if ((recipient == null || recipient.isEmpty()) && !transferValue.isZero()) {
      cost = clampedAdd(cost, newAccountGasCost());
    }

    return cost;
  }

  @Override
  protected long expOperationByteGasCost() {
    return EXP_OPERATION_BYTE_GAS_COST;
  }

  private static final long SELFDESTRUCT_OPERATION_GAS_COST = 5_000L;

  private static final long SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT = 30_000L;

  @Override
  public long selfDestructOperationGasCost(final Account recipient, final Wei inheritance) {
    if ((recipient == null || recipient.isEmpty()) && !inheritance.isZero()) {
      return SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT;
    } else {
      return SELFDESTRUCT_OPERATION_GAS_COST;
    }
  }
}
