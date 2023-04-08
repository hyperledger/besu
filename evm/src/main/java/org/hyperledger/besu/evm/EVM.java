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
import org.hyperledger.besu.evm.code.CodeFactory;
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
import org.hyperledger.besu.evm.operation.JumpDestOperation;
import org.hyperledger.besu.evm.operation.MulOperation;
import org.hyperledger.besu.evm.operation.NotOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.operation.OrOperation;
import org.hyperledger.besu.evm.operation.PopOperation;
import org.hyperledger.besu.evm.operation.Push0Operation;
import org.hyperledger.besu.evm.operation.PushOperation;
import org.hyperledger.besu.evm.operation.SGtOperation;
import org.hyperledger.besu.evm.operation.SLtOperation;
import org.hyperledger.besu.evm.operation.SModOperation;
import org.hyperledger.besu.evm.operation.SignExtendOperation;
import org.hyperledger.besu.evm.operation.StopOperation;
import org.hyperledger.besu.evm.operation.SwapOperation;
import org.hyperledger.besu.evm.operation.VirtualOperation;
import org.hyperledger.besu.evm.operation.XorOperation;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Evm. */
public class EVM {
  private static final Logger LOG = LoggerFactory.getLogger(EVM.class);

  /** The constant OVERFLOW_RESPONSE. */
  protected static final OperationResult OVERFLOW_RESPONSE =
      new OperationResult(0L, ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
  /** The constant UNDERFLOW_RESPONSE. */
  protected static final OperationResult UNDERFLOW_RESPONSE =
      new OperationResult(0L, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);

  private final OperationRegistry operations;
  private final GasCalculator gasCalculator;
  private final Operation endOfScriptStop;
  private final CodeCache codeCache;
  private final EvmSpecVersion evmSpecVersion;

  // Optimized operation flags
  private final boolean enableShanghai;

  /**
   * Instantiates a new Evm.
   *
   * @param operations the operations
   * @param gasCalculator the gas calculator
   * @param evmConfiguration the evm configuration
   * @param evmSpecVersion the evm spec version
   */
  public EVM(
      final OperationRegistry operations,
      final GasCalculator gasCalculator,
      final EvmConfiguration evmConfiguration,
      final EvmSpecVersion evmSpecVersion) {
    this.operations = operations;
    this.gasCalculator = gasCalculator;
    this.endOfScriptStop = new VirtualOperation(new StopOperation(gasCalculator));
    this.codeCache = new CodeCache(evmConfiguration);
    this.evmSpecVersion = evmSpecVersion;

    enableShanghai = EvmSpecVersion.SHANGHAI.ordinal() <= evmSpecVersion.ordinal();
  }

  /**
   * Gets gas calculator.
   *
   * @return the gas calculator
   */
  public GasCalculator getGasCalculator() {
    return gasCalculator;
  }

  /**
   * Gets max eof version.
   *
   * @return the max eof version
   */
  public int getMaxEOFVersion() {
    return evmSpecVersion.maxEofVersion;
  }

  /**
   * Run to halt.
   *
   * @param frame the frame
   * @param tracing the tracing
   */
  // Note to maintainers: lots of Java idioms and OO principals are being set aside in the
  // name of performance. This is one of the hottest sections of code.
  //
  // Please benchmark before refactoring.
  public void runToHalt(final MessageFrame frame, final OperationTracer tracing) {
    evmSpecVersion.maybeWarnVersion();

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
          case 0x0c, 0x0d, 0x0e, 0x0f:
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
          case 0x50: // POP
            result = PopOperation.staticOperation(frame);
            break;
          case 0x5b: // JUMPDEST
            result = JumpDestOperation.jumpdestSuccess;
            break;
          case 0x5f: // PUSH0
            result =
                enableShanghai
                    ? Push0Operation.staticOperation(frame)
                    : InvalidOperation.INVALID_RESULT;
            break;
            // PUSH1-32
          case 0x60,
              0x61,
              0x62,
              0x63,
              0x64,
              0x65,
              0x66,
              0x67,
              0x68,
              0x69,
              0x6a,
              0x6b,
              0x6c,
              0x6d,
              0x6e,
              0x6f,
              0x70,
              0x71,
              0x72,
              0x73,
              0x74,
              0x75,
              0x76,
              0x77,
              0x78,
              0x79,
              0x7a,
              0x7b,
              0x7c,
              0x7d,
              0x7e,
              0x7f:
            result = PushOperation.staticOperation(frame, code, pc, opcode - PUSH_BASE);
            break;
            // DUP1-16
          case 0x80,
              0x81,
              0x82,
              0x83,
              0x84,
              0x85,
              0x86,
              0x87,
              0x88,
              0x89,
              0x8a,
              0x8b,
              0x8c,
              0x8d,
              0x8e,
              0x8f:
            result = DupOperation.staticOperation(frame, opcode - DupOperation.DUP_BASE);
            break;
            // SWAP1-16
          case 0x90,
              0x91,
              0x92,
              0x93,
              0x94,
              0x95,
              0x96,
              0x97,
              0x98,
              0x99,
              0x9a,
              0x9b,
              0x9c,
              0x9d,
              0x9e,
              0x9f:
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
      final ExceptionalHaltReason haltReason = result.getHaltReason();
      if (haltReason != null) {
        LOG.trace("MessageFrame evaluation halted because of {}", haltReason);
        frame.setExceptionalHaltReason(Optional.of(haltReason));
        frame.setState(State.EXCEPTIONAL_HALT);
      } else if (frame.decrementRemainingGas(result.getGasCost()) < 0) {
        frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        frame.setState(State.EXCEPTIONAL_HALT);
      }
      if (frame.getState() == State.CODE_EXECUTING) {
        final int currentPC = frame.getPC();
        final int opSize = result.getPcIncrement();
        frame.setPC(currentPC + opSize);
      }
      if (operationTracer != null) {
        operationTracer.tracePostExecution(frame, result);
      }
    }
  }

  /**
   * Get Operations (unsafe)
   *
   * @return Operations array
   */
  public Operation[] getOperationsUnsafe() {
    return operations.getOperations();
  }

  /**
   * Gets code.
   *
   * @param codeHash the code hash
   * @param codeBytes the code bytes
   * @return the code
   */
  public Code getCode(final Hash codeHash, final Bytes codeBytes) {
    Code result = codeHash == null ? null : codeCache.getIfPresent(codeHash);
    if (result == null) {
      result = CodeFactory.createCode(codeBytes, evmSpecVersion.getMaxEofVersion(), false);
      if (codeHash != null) {
        codeCache.put(codeHash, result);
      }
    }
    return result;
  }
}
