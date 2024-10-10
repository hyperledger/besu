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
public class DelegatedCodeGasCostHelper {

  /** Private constructor to prevent instantiation. */
  private DelegatedCodeGasCostHelper() {
    // empty constructor
  }

  /** The status of the operation. */
  public enum Status {
    /** The operation failed due to insufficient gas. */
    INSUFFICIENT_GAS,
    /** The operation was successful. */
    SUCCESS
  }

  /**
   * The result of the operation.
   *
   * @param gasCost the gas cost
   * @param status of the operation
   */
  public record Result(long gasCost, Status status) {}

  /**
   * Deducts the gas cost for delegated code resolution.
   *
   * @param frame the message frame
   * @param gasCalculator the gas calculator
   * @param account the account
   * @return the gas cost and result of the operation
   */
  public static Result deductDelegatedCodeGasCost(
      final MessageFrame frame, final GasCalculator gasCalculator, final Account account) {
    if (!account.hasDelegatedCode()) {
      return new Result(0, Status.SUCCESS);
    }

    if (account.delegatedCodeAddress().isEmpty()) {
      throw new RuntimeException("A delegated code account must have a delegated code address");
    }

    final long delegatedCodeResolutionGas =
        calculateDelegatedCodeResolutionGas(
            frame, gasCalculator, account.delegatedCodeAddress().get());

    if (frame.getRemainingGas() < delegatedCodeResolutionGas) {
      return new Result(delegatedCodeResolutionGas, Status.INSUFFICIENT_GAS);
    }

    frame.decrementRemainingGas(delegatedCodeResolutionGas);
    return new Result(delegatedCodeResolutionGas, Status.SUCCESS);
  }

  private static long calculateDelegatedCodeResolutionGas(
      final MessageFrame frame, final GasCalculator gasCalculator, final Address delegateeAddress) {
    final boolean delegatedCodeIsWarm =
        frame.warmUpAddress(delegateeAddress) || gasCalculator.isPrecompile(delegateeAddress);
    return delegatedCodeIsWarm
        ? gasCalculator.getWarmStorageReadCost()
        : gasCalculator.getColdAccountAccessCost();
  }
}
