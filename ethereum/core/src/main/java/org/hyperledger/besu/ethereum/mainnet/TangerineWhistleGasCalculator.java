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
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import org.apache.tuweni.units.bigints.UInt256;

public class TangerineWhistleGasCalculator extends FrontierGasCalculator {

  private static final Gas BALANCE_OPERATION_GAS_COST = Gas.of(400L);

  private static final Gas CALL_OPERATION_BASE_GAS_COST = Gas.of(700L);

  private static final Gas EXT_CODE_BASE_GAS_COST = Gas.of(700L);

  private static final Gas SELFDESTRUCT_OPERATION_GAS_COST = Gas.of(5_000L);

  private static final Gas SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT = Gas.of(30_000L);

  private static final Gas SLOAD_OPERATION_GAS_COST = Gas.of(200L);

  @Override
  public Gas getBalanceOperationGasCost() {
    return BALANCE_OPERATION_GAS_COST;
  }

  // Returns all but 1/64 (n - floor(n /16)) of the provided value
  private static Gas allButOneSixtyFourth(final Gas value) {
    return value.minus(value.dividedBy(64));
  }

  @Override
  public Gas callOperationBaseGasCost() {
    return CALL_OPERATION_BASE_GAS_COST;
  }

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
    final Gas inputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, inputDataOffset, inputDataLength);
    final Gas outputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, outputDataOffset, outputDataLength);
    final Gas memoryExpansionCost = inputDataMemoryExpansionCost.max(outputDataMemoryExpansionCost);

    Gas cost = callOperationBaseGasCost().plus(memoryExpansionCost);

    if (!transferValue.isZero()) {
      cost = cost.plus(callValueTransferGasCost());
    }

    if (recipient == null) {
      cost = cost.plus(newAccountGasCost());
    }

    return cost;
  }

  private static Gas gasCap(final Gas remaining, final Gas stipend) {
    return allButOneSixtyFourth(remaining).min(stipend);
  }

  @Override
  public Gas gasAvailableForChildCall(
      final MessageFrame frame, final Gas stipend, final boolean transfersValue) {
    final Gas gasCap = gasCap(frame.getRemainingGas(), stipend);

    // TODO: Integrate this into AbstractCallOperation since it's
    // a little out of place to mutate the frame here.
    frame.decrementRemainingGas(gasCap);

    if (transfersValue) {
      return gasCap.plus(getAdditionalCallStipend());
    } else {
      return gasCap;
    }
  }

  @Override
  public Gas gasAvailableForChildCreate(final Gas stipend) {
    return allButOneSixtyFourth(stipend);
  }

  @Override
  protected Gas extCodeBaseGasCost() {
    return EXT_CODE_BASE_GAS_COST;
  }

  @Override
  public Gas selfDestructOperationGasCost(final Account recipient, final Wei inheritance) {
    if (recipient == null) {
      return SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT;
    } else {
      return SELFDESTRUCT_OPERATION_GAS_COST;
    }
  }

  @Override
  public Gas getSloadOperationGasCost() {
    return SLOAD_OPERATION_GAS_COST;
  }
}
