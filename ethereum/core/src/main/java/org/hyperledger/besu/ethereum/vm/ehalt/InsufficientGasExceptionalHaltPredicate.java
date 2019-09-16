/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.vm.ehalt;

import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.util.EnumSet;
import java.util.Optional;

public class InsufficientGasExceptionalHaltPredicate implements ExceptionalHaltPredicate {

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame, final EnumSet<ExceptionalHaltReason> prevReasons, final EVM evm) {
    // Should not execute, as the cost function could depend on the stack items.
    if (prevReasons.contains(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS)) {
      return Optional.empty();
    }

    try {
      return Optional.ofNullable(frame.getCurrentOperation().cost(frame))
          .filter(cost -> frame.getRemainingGas().compareTo(cost) < 0)
          .map(cost -> ExceptionalHaltReason.INSUFFICIENT_GAS);
    } catch (final IllegalArgumentException e) {
      // TODO: Figure out a better way to handle gas overflows.
      if (e.getMessage().contains("Gas too large")) {
        return Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS);
      }

      // throw e;
      return Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS);
    }
  }
}
