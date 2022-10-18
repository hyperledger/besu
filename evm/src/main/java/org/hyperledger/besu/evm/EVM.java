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
package org.hyperledger.besu.evm;

import static org.hyperledger.besu.evm.operation.PushOperation.PUSH_BASE;
import static org.hyperledger.besu.evm.operation.SwapOperation.SWAP_BASE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.CodeCache;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.internal.FixedStack.OverflowException;
import org.hyperledger.besu.evm.internal.FixedStack.UnderflowException;
import org.hyperledger.besu.evm.operation.AddOperation;
import org.hyperledger.besu.evm.operation.AndOperation;
import org.hyperledger.besu.evm.operation.ByteOperation;
import org.hyperledger.besu.evm.operation.DupOperation;
import org.hyperledger.besu.evm.operation.InvalidOperation;
import org.hyperledger.besu.evm.operation.IsZeroOperation;
import org.hyperledger.besu.evm.operation.MulOperation;
import org.hyperledger.besu.evm.operation.NotOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.operation.OrOperation;
import org.hyperledger.besu.evm.operation.PopOperation;
import org.hyperledger.besu.evm.operation.PushOperation;
import org.hyperledger.besu.evm.operation.SGtOperation;
import org.hyperledger.besu.evm.operation.SLtOperation;
import org.hyperledger.besu.evm.operation.SModOperation;
import org.hyperledger.besu.evm.operation.SarOperation;
import org.hyperledger.besu.evm.operation.ShlOperation;
import org.hyperledger.besu.evm.operation.ShrOperation;
import org.hyperledger.besu.evm.operation.SignExtendOperation;
import org.hyperledger.besu.evm.operation.StopOperation;
import org.hyperledger.besu.evm.operation.SwapOperation;
import org.hyperledger.besu.evm.operation.VirtualOperation;
import org.hyperledger.besu.evm.operation.XorOperation;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.Objects;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EVM {
  private static final Logger LOG = LoggerFactory.getLogger(EVM.class);

  protected static final OperationResult OVERFLOW_RESPONSE =
      new OperationResult(0L, ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
  protected static final OperationResult UNDERFLOW_RESPONSE =
      new OperationResult(0L, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);

  private final OperationRegistry operations;
  private final GasCalculator gasCalculator;
  private final Operation endOfScriptStop;
  private final CodeCache codeCache;

  public EVM(
      final OperationRegistry operations,
      final GasCalculator gasCalculator,
      final EvmConfiguration evmConfiguration) {
    this.operations = operations;
    this.gasCalculator = gasCalculator;
    this.endOfScriptStop = new VirtualOperation(new StopOperation(gasCalculator));
    this.codeCache = new CodeCache(evmConfiguration);
  }

  public GasCalculator getGasCalculator() {
    return gasCalculator;
  }

  // Note to maintainers: lots of Java idioms and OO principals are being set aside in the
  // name of performance. This is one of the hottest sections of code.
  //
  // Please benchmark before refactoring.
  public void runToHalt(final MessageFrame frame, final OperationTracer tracing) {
    var operationTracer = tracing == OperationTracer.NO_TRACING ? null : tracing;
    byte[] code = frame.getCode().getBytes().toArrayUnsafe();
    Operation[] operationArray = operations.getOperations();
    while (frame.getState() == MessageFrame.State.CODE_EXECUTING) {
      Operation currentOperation;
      int opcode;
      int pc = frame.getPC();
      try {
        opcode = code[pc] & 0xff;
        currentOperation = operationArray[opcode];
      } catch (ArrayIndexOutOfBoundsException aiiobe) {
        opcode = 0;
        currentOperation = endOfScriptStop;
      }
      frame.setCurrentOperation(currentOperation);
      if (operationTracer != null) {
        operationTracer.tracePreExecution(frame);
      }

      OperationResult result;
      try {

        switch (opcode) {
            // case 0x00: // STOP
            //  result = StopOperation.staticOperation(frame);
            //  break;
          case 0x01: // ADD
            result = AddOperation.staticOperation(frame);
            break;
          case 0x02: // MUL
            result = MulOperation.staticOperation(frame);
            break;
            // case 0x03: // SUB
            //  result = SubOperation.staticOperation(frame);
            //  break;
            // case 0x04: // DIV
            //  result = DivOperation.staticOperation(frame);
            //  break;
            // case 0x05: // SDIV
            //  result = SDivOperation.staticOperation(frame);
            //  break;
            // case 0x06: // MOD
            //  result = ModOperation.staticOperation(frame);
            //  break;
          case 0x07: // SMOD
            result = SModOperation.staticOperation(frame);
            break;
            // case 0x08: // ADDMOD
            //  result = AddModOperation.staticOperation(frame);
            //  break;
            // case 0x09: // MULMOD
            //  result = MulModOperation.staticOperation(frame);
            //  break;
            // case 0x0a: //EXP requires gasCalculator access, so it is skipped
          case 0x0b: // SIGNEXTEND
            result = SignExtendOperation.staticOperation(frame);
            break;
          case 0x0c:
            result = InvalidOperation.INVALID_RESULT;
            break;
          case 0x0d:
            result = InvalidOperation.INVALID_RESULT;
            break;
          case 0x0e:
            result = InvalidOperation.INVALID_RESULT;
            break;
          case 0x0f:
            result = InvalidOperation.INVALID_RESULT;
            break;
            // case 0x10: // LT
            //  result = LtOperation.staticOperation(frame);
            //  break;
            // case 0x11: // GT
            //  result = GtOperation.staticOperation(frame);
            //  break;
          case 0x12: // SLT
            result = SLtOperation.staticOperation(frame);
            break;
          case 0x13: // SGT
            result = SGtOperation.staticOperation(frame);
            break;
          case 0x15: // ISZERO
            result = IsZeroOperation.staticOperation(frame);
            break;
          case 0x16: // AND
            result = AndOperation.staticOperation(frame);
            break;
          case 0x17: // OR
            result = OrOperation.staticOperation(frame);
            break;
          case 0x18: // XOR
            result = XorOperation.staticOperation(frame);
            break;
          case 0x19: // NOT
            result = NotOperation.staticOperation(frame);
            break;
          case 0x1a: // BYTE
            result = ByteOperation.staticOperation(frame);
            break;
          case 0x1b: // SHL
            result = ShlOperation.staticOperation(frame);
            break;
          case 0x1c: // SHR
            result = ShrOperation.staticOperation(frame);
            break;
          case 0x1d: // SAR
            result = SarOperation.staticOperation(frame);
            break;
          case 0x50: // POP
            result = PopOperation.staticOperation(frame);
            break;
          case 0x60: // PUSH1-32
          case 0x61:
          case 0x62:
          case 0x63:
          case 0x64:
          case 0x65:
          case 0x66:
          case 0x67:
          case 0x68:
          case 0x69:
          case 0x6a:
          case 0x6b:
          case 0x6c:
          case 0x6d:
          case 0x6e:
          case 0x6f:
          case 0x70:
          case 0x71:
          case 0x72:
          case 0x73:
          case 0x74:
          case 0x75:
          case 0x76:
          case 0x77:
          case 0x78:
          case 0x79:
          case 0x7a:
          case 0x7b:
          case 0x7c:
          case 0x7d:
          case 0x7e:
          case 0x7f:
            result = PushOperation.staticOperation(frame, code, pc, opcode - PUSH_BASE);
            break;
          case 0x80: // DUP1-16
          case 0x81:
          case 0x82:
          case 0x83:
          case 0x84:
          case 0x85:
          case 0x86:
          case 0x87:
          case 0x88:
          case 0x89:
          case 0x8a:
          case 0x8b:
          case 0x8c:
          case 0x8d:
          case 0x8e:
          case 0x8f:
            result = DupOperation.staticOperation(frame, opcode - DupOperation.DUP_BASE);
            break;
          case 0x90: // SWAP1-16
          case 0x91:
          case 0x92:
          case 0x93:
          case 0x94:
          case 0x95:
          case 0x96:
          case 0x97:
          case 0x98:
          case 0x99:
          case 0x9a:
          case 0x9b:
          case 0x9c:
          case 0x9d:
          case 0x9e:
          case 0x9f:
            result = SwapOperation.staticOperation(frame, opcode - SWAP_BASE);
            break;
          default: // unoptimized operations
            frame.setCurrentOperation(currentOperation);
            result = currentOperation.execute(frame, this);
            break;
        }
      } catch (final OverflowException oe) {
        result = OVERFLOW_RESPONSE;
      } catch (final UnderflowException ue) {
        result = UNDERFLOW_RESPONSE;
      }
      if (operationTracer != null) {
        operationTracer.tracePostExecution(frame, result);
      }
      final ExceptionalHaltReason haltReason = result.getHaltReason();
      if (haltReason != null) {
        LOG.trace("MessageFrame evaluation halted because of {}", haltReason);
        frame.setExceptionalHaltReason(Optional.of(haltReason));
        frame.setState(State.EXCEPTIONAL_HALT);
      }
      frame.decrementRemainingGas(result.getGasCost());
      if (frame.getState() == State.CODE_EXECUTING) {
        final int currentPC = frame.getPC();
        final int opSize = result.getPcIncrement();
        frame.setPC(currentPC + opSize);
      }
    }
  }

  @VisibleForTesting
  public Operation operationAtOffset(final Code code, final int offset) {
    final Bytes bytecode = code.getBytes();
    // If the length of the program code is shorter than the offset halt execution.
    if (offset >= bytecode.size()) {
      return endOfScriptStop;
    }

    final byte opcode = bytecode.get(offset);
    final Operation operation = operations.get(opcode);
    return Objects.requireNonNullElseGet(operation, () -> new InvalidOperation(opcode, null));
  }

  public Code getCode(final Hash codeHash, final Bytes codeBytes) {
    Code result = codeCache.getIfPresent(codeHash);
    if (result == null) {
      result = new Code(codeBytes, codeHash);
      codeCache.put(codeHash, result);
    }
    return result;
  }
}
