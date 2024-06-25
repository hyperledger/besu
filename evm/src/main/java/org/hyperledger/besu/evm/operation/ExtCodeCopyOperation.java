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
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.code.EOFLayout;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;

import org.apache.tuweni.bytes.Bytes;

/** The Ext code copy operation. */
public class ExtCodeCopyOperation extends AbstractOperation {

  /** This is the "code" legacy contracts see when copying code from an EOF contract. */
  public static final Bytes EOF_REPLACEMENT_CODE = Bytes.fromHexString("0xef00");

  /**
   * Instantiates a new Ext code copy operation.
   *
   * @param gasCalculator the gas calculator
   */
  public ExtCodeCopyOperation(final GasCalculator gasCalculator) {
    super(0x3C, "EXTCODECOPY", 4, 0, gasCalculator);
  }

  /**
   * Cost of Ext Code Copy operation.
   *
   * @param frame the frame
   * @param address to use
   * @param memOffset the mem offset
   * @param sourceOffset the code offset
   * @param codeSize the size of the code <<<<<<< HEAD
   * @param accountIsWarm the account is warm =======
   * @param accountIsWarm true to add warm storage read cost, false to add cold account access cost
   * @return the long
   */
  protected long cost(
      final MessageFrame frame,
      final Address address,
      final long memOffset,
      final long sourceOffset,
      final long readSize,
      final long codeSize,
      final boolean accountIsWarm) {
    return gasCalculator()
        .extCodeCopyOperationGasCost(
            frame, address, accountIsWarm, memOffset, sourceOffset, readSize, codeSize);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final Address address = Words.toAddress(frame.popStackItem());
    final long memOffset = clampedToLong(frame.popStackItem());
    final long sourceOffset = clampedToLong(frame.popStackItem());
    final long readSize = clampedToLong(frame.popStackItem());

    final boolean isPrecompiled = gasCalculator().isPrecompile(address);
    final boolean accountIsWarm = frame.warmUpAddress(address) || isPrecompiled;

    final Account account = frame.getWorldUpdater().get(address);
    // TODO get account and get code before cost checking only for verkle ? maybe move this call
    final Bytes code = account != null ? account.getCode() : Bytes.EMPTY;

    final long cost =
        cost(frame, address, memOffset, sourceOffset, readSize, code.size(), accountIsWarm);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    if (code.size() >= 2 && code.get(0) == EOFLayout.EOF_PREFIX_BYTE && code.get(1) == 0) {
      frame.writeMemory(memOffset, sourceOffset, readSize, EOF_REPLACEMENT_CODE);
    } else {
      frame.writeMemory(memOffset, sourceOffset, readSize, code);
    }

    return new OperationResult(cost, null);
  }
}
