/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.hyperledger.besu.datatypes.Address.BLS12_MAP_FP2_TO_G2;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Gas Calculator for Prague
 *
 * <p>Placeholder for new gas schedule items. If Prague finalzies without changes this can be
 * removed
 *
 * <UL>
 *   <LI>TBD
 * </UL>
 */
public class PragueGasCalculator extends CancunGasCalculator {
  private static final int AUTH_OP_FIXED_FEE = 3100;
  private static final long AUTH_CALL_VALUE_TRANSFER_GAS_COST = 6700;

  /** Instantiates a new Prague Gas Calculator. */
  public PragueGasCalculator() {
    this(BLS12_MAP_FP2_TO_G2.toArrayUnsafe()[19]);
  }

  /**
   * Instantiates a new Prague Gas Calculator
   *
   * @param maxPrecompile the max precompile
   */
  protected PragueGasCalculator(final int maxPrecompile) {
    super(maxPrecompile);
  }

  @Override
  public long authOperationGasCost(
      final MessageFrame frame, final long offset, final long length, final Address authority) {
    final long memoryExpansionGasCost = memoryExpansionGasCost(frame, offset, length);
    final long accessFee = frame.isAddressWarm(authority) ? 100 : 2600;
    final long gasCost = AUTH_OP_FIXED_FEE + memoryExpansionGasCost + accessFee;
    return gasCost;
  }

  /**
   * Returns the gas cost to call another contract on behalf of an authority
   *
   * @return the gas cost to call another contract on behalf of an authority
   */
  @Override
  public long authCallOperationGasCost(
      final MessageFrame frame,
      final long stipend,
      final long inputDataOffset,
      final long inputDataLength,
      final long outputDataOffset,
      final long outputDataLength,
      final Wei transferValue,
      final Account invoker,
      final Address invokee,
      final boolean accountIsWarm) {

    final long inputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, inputDataOffset, inputDataLength);
    final long outputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, outputDataOffset, outputDataLength);
    final long memoryExpansionCost =
        Math.max(inputDataMemoryExpansionCost, outputDataMemoryExpansionCost);

    final long staticGasCost = getWarmStorageReadCost();

    long dynamicGasCost = accountIsWarm ? 0 : getColdAccountAccessCost() - getWarmStorageReadCost();

    if (!transferValue.isZero()) {
      dynamicGasCost += AUTH_CALL_VALUE_TRANSFER_GAS_COST;
    }

    if ((invoker == null || invoker.isEmpty()) && !transferValue.isZero()) {
      dynamicGasCost += newAccountGasCost();
    }

    long cost = staticGasCost + memoryExpansionCost + dynamicGasCost;

    return cost;
  }
}
