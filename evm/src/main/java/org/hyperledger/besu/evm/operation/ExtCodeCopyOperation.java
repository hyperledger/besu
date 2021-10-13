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

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class ExtCodeCopyOperation extends AbstractOperation {

  public ExtCodeCopyOperation(final GasCalculator gasCalculator) {
    super(0x3C, "EXTCODECOPY", 4, 0, false, 1, gasCalculator);
  }

  protected Gas cost(
      final MessageFrame frame,
      final long memOffset,
      final long length,
      final boolean accountIsWarm) {
    return gasCalculator()
        .extCodeCopyOperationGasCost(frame, memOffset, length)
        .plus(
            accountIsWarm
                ? gasCalculator().getWarmStorageReadCost()
                : gasCalculator().getColdAccountAccessCost());
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final Address address = Words.toAddress(frame.popStackItem());
    final long memOffset = clampedToLong(frame.popStackItem());
    final long sourceOffset = clampedToLong(frame.popStackItem());
    final long numBytes = clampedToLong(frame.popStackItem());

    final boolean accountIsWarm =
        frame.warmUpAddress(address) || gasCalculator().isPrecompile(address);
    final Gas cost = cost(frame, memOffset, numBytes, accountIsWarm);

    final Optional<Gas> optionalCost = Optional.of(cost);
    if (frame.getRemainingGas().compareTo(cost) < 0) {
      return new OperationResult(optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    final Account account = frame.getWorldUpdater().get(address);
    final Bytes code = account != null ? account.getCode() : Bytes.EMPTY;

    frame.writeMemory(memOffset, sourceOffset, numBytes, code);
    return new OperationResult(optionalCost, Optional.empty());
  }
}
