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
package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.AbstractOperation;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.FixedStack.OverflowException;
import org.hyperledger.besu.ethereum.vm.FixedStack.UnderflowException;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Words;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class ExtCodeSizeOperation extends AbstractOperation {

  private final Optional<Gas> warmCost;
  private final Optional<Gas> coldCost;

  public ExtCodeSizeOperation(final GasCalculator gasCalculator) {
    super(0x3B, "EXTCODESIZE", 1, 1, false, 1, gasCalculator);
    final Gas baseCost = gasCalculator.getExtCodeSizeOperationGasCost();
    warmCost = Optional.of(baseCost.plus(gasCalculator.getWarmStorageReadCost()));
    coldCost = Optional.of(baseCost.plus(gasCalculator.getColdAccountAccessCost()));
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    try {
      final Address address = Words.toAddress(frame.popStackItem());
      final boolean accountIsWarm =
          frame.warmUpAddress(address) || gasCalculator().isPrecompile(address);
      final Optional<Gas> optionalCost = accountIsWarm ? warmCost : coldCost;
      if (frame.getRemainingGas().compareTo(optionalCost.get()) < 0) {
        return new OperationResult(
            optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
      } else {
        final Account account = frame.getWorldState().get(address);
        frame.pushStackItem(
            account == null ? Bytes32.ZERO : UInt256.valueOf(account.getCode().size()).toBytes());
        return new OperationResult(optionalCost, Optional.empty());
      }
    } catch (final UnderflowException ufe) {
      return new OperationResult(
          warmCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
    } catch (final OverflowException ofe) {
      return new OperationResult(warmCost, Optional.of(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS));
    }
  }
}
