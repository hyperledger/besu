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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class ExceptionalHaltManager {

  private static final List<ExceptionalHaltPredicate> GLOBAL =
      Arrays.asList(
          new InvalidOperationExceptionalHaltPredicate(),
              new StackOverflowExceptionalHaltPredicate(),
          new StackUnderflowExceptionalHaltPredicate(),
              new InsufficientGasExceptionalHaltPredicate());

  public static EnumSet<ExceptionalHaltReason> evaluateAll(
      final MessageFrame frame, final EVM evm) {
    final EnumSet<ExceptionalHaltReason> answer = EnumSet.noneOf(ExceptionalHaltReason.class);
    for (final ExceptionalHaltPredicate predicate : GLOBAL) {
      predicate.exceptionalHaltCondition(frame, answer, evm).ifPresent(answer::add);
    }

    // TODO: Determine whether or not to short-circuit here.
    // Several operations (e.g. JUMP and JUMPI) have stack dependent checks.

    if (!answer.contains(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS)) {
      // Evaluate any operation specific halt conditions too.
      frame
          .getCurrentOperation()
          .exceptionalHaltCondition(frame, answer, evm)
          .ifPresent(answer::add);
    }

    return answer;
  }
}
