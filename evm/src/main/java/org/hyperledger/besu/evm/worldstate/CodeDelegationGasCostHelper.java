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
package org.hyperledger.besu.evm.worldstate;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/**
 * Helper class to deduct gas cost for delegated code resolution.
 *
 * <p>Delegated code resolution is the process of determining the address of the contract that will
 * be executed when a contract has delegated code. This process is necessary to determine the
 * contract that will be executed and to ensure that the contract is warm in the cache.
 */
public class CodeDelegationGasCostHelper {

  /** Private constructor to prevent instantiation. */
  private CodeDelegationGasCostHelper() {
    // empty constructor
  }

  /**
   * Deducts the gas cost for delegated code resolution.
   *
   * @param frame the message frame
   * @param gasCalculator the gas calculator
   * @param account the account
   * @return the gas cost and result of the operation
   */
  public static long codeDelegationGasCost(
      final MessageFrame frame, final GasCalculator gasCalculator, final Account account) {
    if (!account.hasDelegatedCode()) {
      return 0;
    }

    if (account.codeDelegationAddress().isEmpty()) {
      throw new RuntimeException("A delegated code account must have a delegated code address");
    }

    return calculateCodeDelegationResolutionGas(
        frame, gasCalculator, account.codeDelegationAddress().get());
  }

  private static long calculateCodeDelegationResolutionGas(
      final MessageFrame frame, final GasCalculator gasCalculator, final Address delegateeAddress) {
    final boolean isWarm =
        frame.warmUpAddress(delegateeAddress) || gasCalculator.isPrecompile(delegateeAddress);
    return isWarm
        ? gasCalculator.getWarmStorageReadCost()
        : gasCalculator.getColdAccountAccessCost();
  }
}
