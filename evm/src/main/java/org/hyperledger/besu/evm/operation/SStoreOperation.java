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
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.StackMath;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** The SStore operation. */
public class SStoreOperation extends AbstractOperation {

  /** The constant FRONTIER_MINIMUM. */
  public static final long FRONTIER_MINIMUM = 0L;

  /** The constant EIP_1706_MINIMUM. */
  public static final long EIP_1706_MINIMUM = 2300L;

  /** The constant ILLEGAL_STATE_CHANGE. */
  protected static final OperationResult ILLEGAL_STATE_CHANGE =
      new OperationResult(0L, ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);

  private final long minimumGasRemaining;

  /**
   * Instantiates a new SStore operation.
   *
   * @param gasCalculator the gas calculator
   * @param minimumGasRemaining the minimum gas remaining
   */
  public SStoreOperation(final GasCalculator gasCalculator, final long minimumGasRemaining) {
    super(0x55, "SSTORE", 2, 0, gasCalculator);
    this.minimumGasRemaining = minimumGasRemaining;
  }

  /**
   * Gets minimum gas remaining.
   *
   * @return the minimum gas remaining
   */
  public long getMinimumGasRemaining() {
    return minimumGasRemaining;
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {

    if (!frame.stackHasItems(2)) {
      return new OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    }
    final long[] s = frame.stackData();
    final int top = frame.stackTop();
    final org.hyperledger.besu.evm.UInt256 keyNative = StackMath.getAt(s, top, 0);
    final org.hyperledger.besu.evm.UInt256 newValueNative = StackMath.getAt(s, top, 1);
    // Pop 2
    frame.setTop(top - 2);

    final MutableAccount account = getMutableAccount(frame.getRecipientAddress(), frame);
    if (account == null) {
      return ILLEGAL_STATE_CHANGE;
    }

    final long remainingGas = frame.getRemainingGas();

    if (frame.isStatic()) {
      return new OperationResult(remainingGas, ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    }

    if (remainingGas <= minimumGasRemaining) {
      return new OperationResult(minimumGasRemaining, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    // Convert to tuweni types for storage API boundary
    final UInt256 key = keyNative.toTuweni();
    final UInt256 newValue = newValueNative.toTuweni();

    final Address address = account.getAddress();
    final boolean slotIsWarm = frame.warmUpStorage(address, keyNative.toBytes32());
    final Supplier<UInt256> currentValueSupplier =
        Suppliers.memoize(() -> getStorageValue(account, key, frame));
    final Supplier<UInt256> originalValueSupplier =
        Suppliers.memoize(() -> account.getOriginalStorageValue(key));

    final long cost =
        gasCalculator().calculateStorageCost(newValue, currentValueSupplier, originalValueSupplier)
            + (slotIsWarm ? 0L : gasCalculator().getColdSloadCost());
    if (remainingGas < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    // Increment the refund counter.
    frame.incrementGasRefund(
        gasCalculator()
            .calculateStorageRefundAmount(newValue, currentValueSupplier, originalValueSupplier));

    account.setStorageValue(key, newValue);
    frame.storageWasUpdated(key, Bytes.wrap(newValueNative.toBytesBE()));
    frame.getEip7928AccessList().ifPresent(t -> t.addSlotAccessForAccount(address, key));

    return new OperationResult(cost, null);
  }
}
