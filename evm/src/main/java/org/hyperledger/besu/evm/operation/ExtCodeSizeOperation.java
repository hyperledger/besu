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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack.OverflowException;
import org.hyperledger.besu.evm.internal.FixedStack.UnderflowException;
import org.hyperledger.besu.evm.internal.Words;

import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.units.bigints.UInt256;

public class ExtCodeSizeOperation extends AbstractOperation {

  public ExtCodeSizeOperation(final GasCalculator gasCalculator) {
    super(0x3B, "EXTCODESIZE", 1, 1, 1, gasCalculator);
  }

  protected long cost(final boolean accountIsWarm) {
    return gasCalculator().getExtCodeSizeOperationGasCost()
        + (accountIsWarm
            ? gasCalculator().getWarmStorageReadCost()
            : gasCalculator().getColdAccountAccessCost());
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    try {
      final Address address = Words.toAddress(frame.popStackItem());
      final boolean accountIsWarm =
          frame.warmUpAddress(address) || gasCalculator().isPrecompile(address);
      final long cost = cost(accountIsWarm);
      if (frame.getRemainingGas() < cost) {
        return new OperationResult(
            OptionalLong.of(cost), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
      } else {
        final Account account = frame.getWorldUpdater().get(address);
        frame.pushStackItem(
            account == null ? UInt256.ZERO : UInt256.valueOf(account.getCode().size()));
        return new OperationResult(OptionalLong.of(cost), Optional.empty());
      }
    } catch (final UnderflowException ufe) {
      return new OperationResult(
          OptionalLong.of(cost(true)), Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
    } catch (final OverflowException ofe) {
      return new OperationResult(
          OptionalLong.of(cost(true)), Optional.of(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS));
    }
  }
}
