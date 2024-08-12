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

import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;

import java.util.Optional;

public class DelegatedCodeGasCostHelper {
  public static Optional<Operation.OperationResult> deductDelegatedCodeGasCost(
      final MessageFrame frame, final GasCalculator gasCalculator, final Account contract) {
    if (!contract.hasDelegatedCode()) {
      return Optional.empty();
    }

    if (contract.delegatedCodeAddress().isEmpty()) {
      throw new RuntimeException("A delegated code account must have a delegated code address");
    }

    final boolean delegatedCodeIsWarm = frame.warmUpAddress(contract.delegatedCodeAddress().get());
    final long delegatedCodeResolutionGas =
        gasCalculator.delegatedCodeResolutionGasCost(delegatedCodeIsWarm);

    if (frame.getRemainingGas() < delegatedCodeResolutionGas) {
      return Optional.of(
          new Operation.OperationResult(
              delegatedCodeResolutionGas, ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    frame.decrementRemainingGas(delegatedCodeResolutionGas);
    return Optional.empty();
  }
}
