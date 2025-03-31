package org.hyperledger.besu.evm.operation;

import static org.hyperledger.besu.evm.internal.Words.clampedAdd;
import static org.hyperledger.besu.evm.operation.AbstractExtCallOperation.EOF1_EXCEPTION_STACK_ITEM;
import static org.hyperledger.besu.evm.operation.AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;

public class PayOperation extends AbstractOperation {
  static final int OPCODE = 0xfc;
  /**
   * Instantiates a new Abstract operation.
   *
   * @param gasCalculator the gas calculator
   */
  public PayOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "PAY", 2, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    Code callingCode = frame.getCode();
    if (callingCode.getEofVersion() == 0) {
      return InvalidOperation.INVALID_RESULT;
    }

    final Bytes toAddressBytes = frame.getStackItem(1);
    if (toAddressBytes.size() > 20
      && toAddressBytes.numberOfLeadingZeroBytes() < 12) {
      return new OperationResult(0, ExceptionalHaltReason.ADDRESS_OUT_OF_RANGE);
    }

    final Address to = Words.toAddress(toAddressBytes);
    final Wei value = Wei.wrap(frame.getStackItem(0));
    final boolean hasValue = value.greaterThan(Wei.ZERO);
    final Account recipient = frame.getWorldUpdater().get(to);

    final boolean accountIsWarm = frame.warmUpAddress(to);

    if (frame.isStatic() && hasValue && !Objects.equals(frame.getSenderAddress(), to)) {
      return new OperationResult(cost(to, true, recipient, true), ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    }

    final long cost = cost(to, hasValue, recipient, accountIsWarm);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    if (!hasValue || Objects.equals(frame.getSenderAddress(), to)) {
      frame.popStackItems(getStackItemsConsumed());
      frame.pushStackItem(EOF1_SUCCESS_STACK_ITEM);
      return new OperationResult(cost(to, hasValue, recipient, accountIsWarm), null);
    }

    final MutableAccount senderAccount = frame.getWorldUpdater().getSenderAccount(frame);
    if (value.compareTo(senderAccount.getBalance()) > 0) {
      frame.popStackItems(getStackItemsConsumed());
      frame.pushStackItem(EOF1_EXCEPTION_STACK_ITEM);
      return new OperationResult(cost, null);
    }

    final MutableAccount recipientAccount = frame.getWorldUpdater().getOrCreate(to);
    senderAccount.decrementBalance(value);
    recipientAccount.incrementBalance(value);

    frame.popStackItems(getStackItemsConsumed());
    frame.pushStackItem(EOF1_SUCCESS_STACK_ITEM);
    return new OperationResult(cost, null);
  }

  private long cost(final Address to, final boolean hasValue, final Account recipient, final boolean accountIsWarm) {
    long cost = 0;
    if (hasValue) {
      cost = gasCalculator().callValueTransferGasCost();
    }
    if (accountIsWarm
      //TODO: what about precompile accounts?
      || gasCalculator().isPrecompile(to)) {
      return clampedAdd(cost, gasCalculator().getWarmStorageReadCost());
    }

    cost = clampedAdd(cost, gasCalculator().getColdAccountAccessCost());

    if (recipient == null) {
      cost = clampedAdd(cost, gasCalculator().newAccountGasCost());
    }

    return cost;
  }
}
