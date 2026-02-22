/*
 * Copyright contributors to Besu.
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

import static org.hyperledger.besu.evm.internal.Words.clampedAdd;
import static org.hyperledger.besu.evm.operation.AbstractCallOperation.LEGACY_FAILURE_STACK_ITEM;
import static org.hyperledger.besu.evm.operation.AbstractCallOperation.LEGACY_SUCCESS_STACK_ITEM;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;

/** The PAY operation */
public class PayOperation extends AbstractOperation {

  /**
   * Instantiates a new Abstract operation.
   *
   * @param gasCalculator the gas calculator
   */
  public PayOperation(final GasCalculator gasCalculator) {
    super(0xfc, "PAY", 2, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    if (frame.isStatic()) {
      return new OperationResult(0, ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    }

    final org.hyperledger.besu.evm.UInt256 toAddrUint = frame.getStackItem(0);
    // Check if value has more than 20 bytes of significant data (u3 must be 0, upper 32 bits of u2 must be 0)
    if (toAddrUint.u3() != 0 || (toAddrUint.u2() >>> 32) != 0) {
      return new OperationResult(0, ExceptionalHaltReason.ADDRESS_OUT_OF_RANGE);
    }

    final Address to = Words.toAddress(toAddrUint);
    final Wei value = Wei.wrap(Bytes.wrap(frame.getStackItem(1).toBytesBE()));
    final boolean hasValue = value.greaterThan(Wei.ZERO);
    final Account recipient = getAccount(to, frame);

    final boolean accountIsWarm = frame.warmUpAddress(to);

    final long cost = cost(to, hasValue, recipient, accountIsWarm);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    if (!hasValue || Objects.equals(frame.getSenderAddress(), to)) {
      frame.popStackItems(getStackItemsConsumed());
      frame.pushStackItem(org.hyperledger.besu.evm.UInt256.fromInt(1));
      return new OperationResult(cost, null);
    }

    final MutableAccount senderAccount = getSenderAccount(frame);
    if (value.compareTo(senderAccount.getBalance()) > 0) {
      frame.popStackItems(getStackItemsConsumed());
      frame.pushStackItem(org.hyperledger.besu.evm.UInt256.ZERO);
      return new OperationResult(cost, null);
    }

    final MutableAccount recipientAccount = getOrCreateAccount(to, frame);
    senderAccount.decrementBalance(value);
    recipientAccount.incrementBalance(value);

    frame.popStackItems(getStackItemsConsumed());
    frame.pushStackItem(org.hyperledger.besu.evm.UInt256.fromInt(1));
    return new OperationResult(cost, null);
  }

  private long cost(
      final Address to,
      final boolean hasValue,
      final Account recipient,
      final boolean accountIsWarm) {
    long cost = 0;
    if (hasValue) {
      cost = gasCalculator().callValueTransferGasCost();
    }
    if (accountIsWarm || gasCalculator().isPrecompile(to)) {
      return clampedAdd(cost, gasCalculator().getWarmStorageReadCost());
    }

    cost = clampedAdd(cost, gasCalculator().getColdAccountAccessCost());

    if (recipient == null && hasValue) {
      cost = clampedAdd(cost, gasCalculator().newAccountGasCost());
    }

    return cost;
  }
}
