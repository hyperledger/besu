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
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.internal.JumpDestOnlyCodeCache;
import org.hyperledger.besu.evm.internal.OverflowException;
import org.hyperledger.besu.evm.internal.UnderflowException;
import org.hyperledger.besu.evm.operation.AddModOperation;
import org.hyperledger.besu.evm.operation.AddModOperationOptimized;
import org.hyperledger.besu.evm.operation.AddOperation;
import org.hyperledger.besu.evm.operation.AddOperationOptimized;
import org.hyperledger.besu.evm.operation.AndOperation;
import org.hyperledger.besu.evm.operation.AndOperationOptimized;
import org.hyperledger.besu.evm.operation.ByteOperation;
import org.hyperledger.besu.evm.operation.ChainIdOperation;
import org.hyperledger.besu.evm.operation.CountLeadingZerosOperation;
import org.hyperledger.besu.evm.operation.DivOperation;
import org.hyperledger.besu.evm.operation.DivOperationOptimized;
import org.hyperledger.besu.evm.operation.DupNOperation;
import org.hyperledger.besu.evm.operation.DupOperation;
import org.hyperledger.besu.evm.operation.ExchangeOperation;
import org.hyperledger.besu.evm.operation.ExpOperation;
import org.hyperledger.besu.evm.operation.GtOperation;
import org.hyperledger.besu.evm.operation.InvalidOperation;
import org.hyperledger.besu.evm.operation.IsZeroOperation;
import org.hyperledger.besu.evm.operation.JumpDestOperation;
import org.hyperledger.besu.evm.operation.JumpOperation;
import org.hyperledger.besu.evm.operation.JumpiOperation;
import org.hyperledger.besu.evm.operation.LtOperation;
import org.hyperledger.besu.evm.operation.ModOperation;
import org.hyperledger.besu.evm.operation.ModOperationOptimized;
import org.hyperledger.besu.evm.operation.MulModOperation;
import org.hyperledger.besu.evm.operation.MulModOperationOptimized;
import org.hyperledger.besu.evm.operation.MulOperation;
import org.hyperledger.besu.evm.operation.MulOperationOptimized;
import org.hyperledger.besu.evm.operation.NotOperation;
import org.hyperledger.besu.evm.operation.NotOperationOptimized;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.operation.OrOperation;
import org.hyperledger.besu.evm.operation.OrOperationOptimized;
import org.hyperledger.besu.evm.operation.PopOperation;
import org.hyperledger.besu.evm.operation.Push0Operation;
import org.hyperledger.besu.evm.operation.PushOperation;
import org.hyperledger.besu.evm.operation.SDivOperation;
import org.hyperledger.besu.evm.operation.SDivOperationOptimized;
import org.hyperledger.besu.evm.operation.SGtOperation;
import org.hyperledger.besu.evm.operation.SLtOperation;
import org.hyperledger.besu.evm.operation.SModOperation;
import org.hyperledger.besu.evm.operation.SModOperationOptimized;
import org.hyperledger.besu.evm.operation.SarOperation;
import org.hyperledger.besu.evm.operation.SarOperationOptimized;
import org.hyperledger.besu.evm.operation.ShlOperation;
import org.hyperledger.besu.evm.operation.ShlOperationOptimized;
import org.hyperledger.besu.evm.operation.ShrOperation;
import org.hyperledger.besu.evm.operation.ShrOperationOptimized;
import org.hyperledger.besu.evm.operation.SignExtendOperation;
import org.hyperledger.besu.evm.operation.StopOperation;
import org.hyperledger.besu.evm.operation.SubOperation;
import org.hyperledger.besu.evm.operation.SubOperationOptimized;
import org.hyperledger.besu.evm.operation.SwapNOperation;
import org.hyperledger.besu.evm.operation.SwapOperation;
import org.hyperledger.besu.evm.operation.VirtualOperation;
import org.hyperledger.besu.evm.operation.XorOperation;
import org.hyperledger.besu.evm.operation.XorOperationOptimized;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.v2.operation.AbstractOperationV2;
import org.hyperledger.besu.evm.v2.operation.AddModOperationV2;
import org.hyperledger.besu.evm.v2.operation.AddOperationV2;
import org.hyperledger.besu.evm.v2.operation.AndOperationV2;
import org.hyperledger.besu.evm.v2.operation.ByteOperationV2;
import org.hyperledger.besu.evm.v2.operation.CountLeadingZerosOperationV2;
import org.hyperledger.besu.evm.v2.operation.DivOperationV2;
import org.hyperledger.besu.evm.v2.operation.DupNOperationV2;
import org.hyperledger.besu.evm.v2.operation.DupOperationV2;
import org.hyperledger.besu.evm.v2.operation.ExchangeOperationV2;
import org.hyperledger.besu.evm.v2.operation.ExpOperationV2;
import org.hyperledger.besu.evm.v2.operation.GtOperationV2;
import org.hyperledger.besu.evm.v2.operation.InvalidOperationV2;
import org.hyperledger.besu.evm.v2.operation.IsZeroOperationV2;
import org.hyperledger.besu.evm.v2.operation.JumpDestOperationV2;
import org.hyperledger.besu.evm.v2.operation.JumpOperationV2;
import org.hyperledger.besu.evm.v2.operation.JumpiOperationV2;
import org.hyperledger.besu.evm.v2.operation.LtOperationV2;
import org.hyperledger.besu.evm.v2.operation.ModOperationV2;
import org.hyperledger.besu.evm.v2.operation.MulModOperationV2;
import org.hyperledger.besu.evm.v2.operation.MulOperationV2;
import org.hyperledger.besu.evm.v2.operation.NotOperationV2;
import org.hyperledger.besu.evm.v2.operation.OperationsV2;
import org.hyperledger.besu.evm.v2.operation.OrOperationV2;
import org.hyperledger.besu.evm.v2.operation.PopOperationV2;
import org.hyperledger.besu.evm.v2.operation.PushOperationV2;
import org.hyperledger.besu.evm.v2.operation.SDivOperationV2;
import org.hyperledger.besu.evm.v2.operation.SGtOperationV2;
import org.hyperledger.besu.evm.v2.operation.SLtOperationV2;
import org.hyperledger.besu.evm.v2.operation.SModOperationV2;
import org.hyperledger.besu.evm.v2.operation.SarOperationV2;
import org.hyperledger.besu.evm.v2.operation.ShlOperationV2;
import org.hyperledger.besu.evm.v2.operation.ShrOperationV2;
import org.hyperledger.besu.evm.v2.operation.SignExtendOperationV2;
import org.hyperledger.besu.evm.v2.operation.StopOperationV2;
import org.hyperledger.besu.evm.v2.operation.SubOperationV2;
import org.hyperledger.besu.evm.v2.operation.SwapNOperationV2;
import org.hyperledger.besu.evm.v2.operation.SwapOperationV2;
import org.hyperledger.besu.evm.v2.operation.XorOperationV2;

import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.jspecify.annotations.NonNull;
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
  private final EvmConfiguration evmConfiguration;
  private final EvmSpecVersion evmSpecVersion;

  // Optimized operation flags
  private final boolean enableConstantinople;
  private final boolean enableShanghai;
  private final boolean enableAmsterdam;
  private final boolean enableOsaka;

  private final JumpDestOnlyCodeCache jumpDestOnlyCodeCache;

  private final Operation[] operationsV2;

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
    this.evmConfiguration = evmConfiguration;
    this.evmSpecVersion = evmSpecVersion;
    this.jumpDestOnlyCodeCache = new JumpDestOnlyCodeCache(evmConfiguration);

    enableConstantinople = EvmSpecVersion.CONSTANTINOPLE.ordinal() <= evmSpecVersion.ordinal();
    enableShanghai = EvmSpecVersion.SHANGHAI.ordinal() <= evmSpecVersion.ordinal();
    enableAmsterdam = EvmSpecVersion.AMSTERDAM.ordinal() <= evmSpecVersion.ordinal();
    enableOsaka = EvmSpecVersion.OSAKA.ordinal() <= evmSpecVersion.ordinal();

    operationsV2 =
        evmConfiguration.enableEvmV2()
            ? OperationsV2.of(operations.getOperations(), gasCalculator, evmSpecVersion)
            : null;
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
   * Gets the max code size, taking configuration and version into account
   *
   * @return The max code size override, if not set the max code size for the EVM version.
   */
  public int getMaxCodeSize() {
    return evmConfiguration.maxCodeSizeOverride().orElse(evmSpecVersion.maxCodeSize);
  }

  /**
   * Gets the max initcode Size, taking configuration and version into account
   *
   * @return The max initcode size override, if not set the max initcode size for the EVM version.
   */
  public int getMaxInitcodeSize() {
    return evmConfiguration.maxInitcodeSizeOverride().orElse(evmSpecVersion.maxInitcodeSize);
  }

  /**
   * Returns the non-fork related configuration parameters of the EVM.
   *
   * @return the EVM configuration.
   */
  public EvmConfiguration getEvmConfiguration() {
    return evmConfiguration;
  }

  /**
   * Returns the configured EVM spec version for this EVM
   *
   * @return the evm spec version
   */
  public EvmSpecVersion getEvmVersion() {
    return evmSpecVersion;
  }

  /**
   * Return the ChainId this Executor is using, or empty if the EVM version does not expose chain
   * ID.
   *
   * @return the ChainId, or empty if not exposed.
   */
  public Optional<Bytes> getChainId() {
    Operation op = operations.get(ChainIdOperation.OPCODE);
    if (op instanceof ChainIdOperation chainIdOperation) {
      return Optional.of(chainIdOperation.getChainId());
    } else {
      return Optional.empty();
    }
  }

  /**
   * Run to halt.
   *
   * @param frame the frame
   * @param operationTracer the tracing
   */
  // Note to maintainers: lots of Java idioms and OO principals are being set aside in the
  // name of performance. This is one of the hottest sections of code.
  //
  // Please benchmark before refactoring.
  public void runToHalt(final MessageFrame frame, @NonNull final OperationTracer operationTracer) {
    // do not remove assert! A single, monomorphic tracer, is allowed in the EVM execution if
    // tracing is disabled for
    // optimization purposes
    assert operationTracer.isEnabled() || operationTracer == OperationTracer.NO_TRACING;

    if (evmConfiguration.enableEvmV2()) {
      runToHaltV2(frame, operationTracer);
      return;
    }
    evmSpecVersion.maybeWarnVersion();

    byte[] code = frame.getCode().getBytes().toArrayUnsafe();
    Operation[] operationArray = operations.getOperations();
    // A node that builds blocks also runs this loop with the interruptible selection tracer, so the
    // tracer call sites are not monomorphic; skip them entirely when tracing is disabled.
    final boolean tracingEnabled = operationTracer.isEnabled();
    while (frame.getState() == MessageFrame.State.CODE_EXECUTING) {
      Operation currentOperation;
      int opcode;
      int pc = frame.getPC();
      if (pc < code.length) {
        opcode = code[pc] & 0xff;
        currentOperation = operationArray[opcode];
      } else {
        opcode = 0;
        currentOperation = endOfScriptStop;
      }
      frame.setCurrentOperation(currentOperation);
      if (tracingEnabled) {
        operationTracer.tracePreExecution(frame);
      }

      OperationResult result;
      try {
        result =
            switch (opcode) {
              case 0x00 -> StopOperation.staticOperation(frame);
              case 0x01 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? AddOperationOptimized.staticOperation(frame)
                      : AddOperation.staticOperation(frame);
              case 0x02 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? MulOperationOptimized.staticOperation(frame)
                      : MulOperation.staticOperation(frame);
              case 0x03 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? SubOperationOptimized.staticOperation(frame)
                      : SubOperation.staticOperation(frame);
              case 0x04 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? DivOperationOptimized.staticOperation(frame)
                      : DivOperation.staticOperation(frame);
              case 0x05 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? SDivOperationOptimized.staticOperation(frame)
                      : SDivOperation.staticOperation(frame);
              case 0x06 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? ModOperationOptimized.staticOperation(frame)
                      : ModOperation.staticOperation(frame);
              case 0x07 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? SModOperationOptimized.staticOperation(frame)
                      : SModOperation.staticOperation(frame);
              case 0x08 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? AddModOperationOptimized.staticOperation(frame)
                      : AddModOperation.staticOperation(frame);
              case 0x09 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? MulModOperationOptimized.staticOperation(frame)
                      : MulModOperation.staticOperation(frame);
              case 0x0a -> ExpOperation.staticOperation(frame, gasCalculator);
              case 0x0b -> SignExtendOperation.staticOperation(frame);
              case 0x0c, 0x0d, 0x0e, 0x0f -> InvalidOperation.invalidOperationResult(opcode);
              case 0x10 -> LtOperation.staticOperation(frame);
              case 0x11 -> GtOperation.staticOperation(frame);
              case 0x12 -> SLtOperation.staticOperation(frame);
              case 0x13 -> SGtOperation.staticOperation(frame);
              case 0x15 -> IsZeroOperation.staticOperation(frame);
              case 0x16 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? AndOperationOptimized.staticOperation(frame)
                      : AndOperation.staticOperation(frame);
              case 0x17 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? OrOperationOptimized.staticOperation(frame)
                      : OrOperation.staticOperation(frame);
              case 0x18 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? XorOperationOptimized.staticOperation(frame)
                      : XorOperation.staticOperation(frame);
              case 0x19 ->
                  evmConfiguration.enableOptimizedOpcodes()
                      ? NotOperationOptimized.staticOperation(frame)
                      : NotOperation.staticOperation(frame);
              case 0x1a -> ByteOperation.staticOperation(frame);
              case 0x1b ->
                  enableConstantinople
                      ? shiftOperation(
                          frame,
                          ShlOperation::staticOperation,
                          ShlOperationOptimized::staticOperation)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x1c ->
                  enableConstantinople
                      ? shiftOperation(
                          frame,
                          ShrOperation::staticOperation,
                          ShrOperationOptimized::staticOperation)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x1d ->
                  enableConstantinople
                      ? shiftOperation(
                          frame,
                          SarOperation::staticOperation,
                          SarOperationOptimized::staticOperation)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x1e ->
                  enableOsaka
                      ? CountLeadingZerosOperation.staticOperation(frame)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x50 -> PopOperation.staticOperation(frame);
              case 0x56 -> JumpOperation.staticOperation(frame);
              case 0x57 -> JumpiOperation.staticOperation(frame);
              case 0x5b -> JumpDestOperation.JUMPDEST_SUCCESS;
              case 0x5f ->
                  enableShanghai
                      ? Push0Operation.staticOperation(frame)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x60, // PUSH1-32
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
                  0x7f ->
                  PushOperation.staticOperation(frame, code, pc, opcode - PUSH_BASE);
              case 0x80, // DUP1-16
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
                  0x8f ->
                  DupOperation.staticOperation(frame, opcode - DupOperation.DUP_BASE);
              case 0x90, // SWAP1-16
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
                  0x9f ->
                  SwapOperation.staticOperation(frame, opcode - SWAP_BASE);
              case 0xe6 -> // DUPN (EIP-8024)
                  enableAmsterdam
                      ? DupNOperation.staticOperation(frame, code, pc)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0xe7 -> // SWAPN (EIP-8024)
                  enableAmsterdam
                      ? SwapNOperation.staticOperation(frame, code, pc)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0xe8 -> // EXCHANGE (EIP-8024)
                  enableAmsterdam
                      ? ExchangeOperation.staticOperation(frame, code, pc)
                      : InvalidOperation.invalidOperationResult(opcode);
              default -> { // unoptimized operations
                frame.setCurrentOperation(currentOperation);
                yield currentOperation.execute(frame, this);
              }
            };
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
      if (tracingEnabled) {
        operationTracer.tracePostExecution(frame, result);
      }
    }
  }

  /**
   * EVM v2 execution loop using long[] stack representation. The opcodes listed in the switches are
   * executed directly; all others by the v2 versions of the fork's registered operations.
   */
  // Note: like runToHalt, this is performance-critical code. Benchmark before refactoring.
  private void runToHaltV2(final MessageFrame frame, final OperationTracer operationTracer) {
    evmSpecVersion.maybeWarnVersion();
    if (operationTracer.tracesOperations()) {
      runToHaltV2Traced(frame, operationTracer);
    } else {
      runToHaltV2Untraced(frame);
    }
  }

  // The untraced loop switches on a dense case index: a table up to the highest copied opcode would
  // be loop bytecode, which counts against C2's inlining budget.
  // BEGIN GENERATED case table for the copies of the operations' code in the untraced loop
  // below; do not edit, run ./gradlew :evm:generateEvmV2Loop
  private static final int COPIED_PUSH1 = 1;
  private static final int COPIED_PUSH2_TO_8 = 2;
  private static final int COPIED_DUP = 3;
  private static final int COPIED_SWAP = 4;
  private static final int COPIED_JUMPDEST = 5;
  private static final int COPIED_POP = 6;
  private static final int COPIED_ADD = 7;
  private static final int COPIED_ISZERO = 8;
  private static final int COPIED_AND = 9;
  private static final int COPIED_SUB = 10;
  private static final int COPIED_LT = 11;
  private static final int COPIED_SHL = 12;
  private static final int COPIED_GT = 13;
  private static final int COPIED_PUSH0 = 14;
  private static final int COPIED_SHR = 15;
  private static final int COPIED_OR = 16;
  private static final int COPIED_NOT = 17;
  private static final int COPIED_SLT = 18;

  // Per opcode: its case in the low byte, and above it the distance of the case's
  // constant from the stack top and pc that its copied code reads
  private static final int[] DISPATCH = new int[256];

  static {
    dispatch(COPIED_PUSH1, 0x60, 0x60);
    dispatch(COPIED_PUSH2_TO_8, 0x61, 0x67);
    dispatch(COPIED_DUP, 0x80, 0x8f);
    dispatch(COPIED_SWAP, 0x90, 0x9f);
    dispatch(COPIED_JUMPDEST, 0x5b, 0x5b);
    dispatch(COPIED_POP, 0x50, 0x50);
    dispatch(COPIED_ADD, 0x01, 0x01);
    dispatch(COPIED_ISZERO, 0x15, 0x15);
    dispatch(COPIED_AND, 0x16, 0x16);
    dispatch(COPIED_SUB, 0x03, 0x03);
    dispatch(COPIED_LT, 0x10, 0x10);
    dispatch(COPIED_SHL, 0x1b, 0x1b);
    dispatch(COPIED_GT, 0x11, 0x11);
    dispatch(COPIED_PUSH0, 0x5f, 0x5f);
    dispatch(COPIED_SHR, 0x1c, 0x1c);
    dispatch(COPIED_OR, 0x17, 0x17);
    dispatch(COPIED_NOT, 0x19, 0x19);
    dispatch(COPIED_SLT, 0x12, 0x12);
  }

  private static void dispatch(final int copiedCase, final int first, final int last) {
    for (int op = first; op <= last; op++) {
      DISPATCH[op] = ((copiedCase * 64) << 8) | copiedCase;
    }
  }

  // copied from StackUtil.setLong
  private static void stackUtilSetLong(final long value, final long[] stack, final int top) {
    final int offset = top << 2;
    stack[offset] = 0L;
    stack[offset + 1] = 0L;
    stack[offset + 2] = 0L;
    stack[offset + 3] = value;
  }

  // copied from StackUtil.copyWord
  private static void stackUtilCopyWord(final long[] stack, final int from, final int to) {
    final int src = from << 2;
    final int dst = to << 2;
    stack[dst] = stack[src];
    stack[dst + 1] = stack[src + 1];
    stack[dst + 2] = stack[src + 2];
    stack[dst + 3] = stack[src + 3];
  }

  // copied from StackUtil.swapWords
  private static void stackUtilSwapWords(final long[] stack, final int a, final int b) {
    final int aOffset = a << 2;
    final int bOffset = b << 2;
    final long a0 = stack[aOffset];
    final long a1 = stack[aOffset + 1];
    final long a2 = stack[aOffset + 2];
    final long a3 = stack[aOffset + 3];
    stack[aOffset] = stack[bOffset];
    stack[aOffset + 1] = stack[bOffset + 1];
    stack[aOffset + 2] = stack[bOffset + 2];
    stack[aOffset + 3] = stack[bOffset + 3];
    stack[bOffset] = a0;
    stack[bOffset + 1] = a1;
    stack[bOffset + 2] = a2;
    stack[bOffset + 3] = a3;
  }

  // copied from StackUtil.isZeroAt
  private static boolean stackUtilIsZeroAt(final long[] stack, final int top, final int depth) {
    final int off = (top - 1 - depth) << 2;
    return (stack[off] | stack[off + 1] | stack[off + 2] | stack[off + 3]) == 0;
  }

  // copied from StackUtil.compareUnsigned
  private static int stackUtilCompareUnsigned(
      final long[] stack, final int aOffset, final int bOffset) {
    if (stack[aOffset] != stack[bOffset]) {
      return Long.compareUnsigned(stack[aOffset], stack[bOffset]);
    }
    if (stack[aOffset + 1] != stack[bOffset + 1]) {
      return Long.compareUnsigned(stack[aOffset + 1], stack[bOffset + 1]);
    }
    if (stack[aOffset + 2] != stack[bOffset + 2]) {
      return Long.compareUnsigned(stack[aOffset + 2], stack[bOffset + 2]);
    }
    return Long.compareUnsigned(stack[aOffset + 3], stack[bOffset + 3]);
  }

  // copied from StackUtil.compareSigned
  private static int stackUtilCompareSigned(
      final long[] stack, final int aOffset, final int bOffset) {
    if (stack[aOffset] != stack[bOffset]) {
      return Long.compare(stack[aOffset], stack[bOffset]);
    }
    return stackUtilCompareUnsigned(stack, aOffset, bOffset);
  }

  // END GENERATED case table

  /**
   * The v2 interpreter loop for untraced execution, which is every block import. The program
   * counter, the gas and the stack live in locals, so that C2 keeps them in registers, and the most
   * frequent operations run inline, as copies of their own code. Every other operation, and every
   * operation that does not end with one of its constant successes, goes through {@link
   * #executeOperationV2} and {@link #completeOperation}, with the state handed to the frame and
   * read back after.
   *
   * <p>The cases between the generated markers are written by {@code ./gradlew
   * :evm:generateEvmV2Loop} from the operations' code, which the build checks they match; see
   * EvmV2LoopGenerator.
   */
  private void runToHaltV2Untraced(final MessageFrame frame) {
    if (frame.getState() != State.CODE_EXECUTING) {
      return;
    }
    final Code codeObject = frame.getCode();
    final byte[] code = codeObject.getBytes().toArrayUnsafe();
    final boolean constantinople = enableConstantinople;
    final boolean shanghai = enableShanghai;
    final long[] s = frame.stackDataV2();
    // the stack array holds as many items as the stack may
    final int maxItems = s.length >> 2;
    int sp = frame.stackTopV2();
    int pc = frame.getPC();
    long gas = frame.getRemainingGas();
    while (true) {
      final int opcode = pc < code.length ? code[pc] & 0xff : 0;
      // set by a copy that succeeded, and applied once below: the same update in every case would
      // be one computation, which C2 places before the dispatch
      long cost = -1;
      int step = 0;
      OperationResult result = null;
      // each case reads the stack top and pc as a base plus a constant of its own, which the table
      // cancels, so that their indices are expressions of their own and stay in the case
      final int entry = DISPATCH[opcode];
      final int bias = entry >> 8;
      final int spBase = sp - bias;
      final int pcBase = pc - bias;
      switch (entry & 0xff) {
        // BEGIN GENERATED cases: copies of the operations' code;
        // edit those, then run ./gradlew :evm:generateEvmV2Loop
        // BEGIN copied from PushOperationV2.SingleByte.staticOperation
        case COPIED_PUSH1 -> {
          int pushSize = 1;
          if (!((spBase + 64) + 1 <= maxItems)) {
            frame.setTopV2(sp);
            frame.setPC(pc);
            frame.setGasRemaining(gas);
            result = AbstractOperationV2.OVERFLOW_RESPONSE;
            break;
          }
          long u0 = 0;
          final int start = (pcBase + 64) + 1;
          if (pushSize != 0 && start < code.length) {
            u0 = code[start] & 0xFFL;
          }
          final int top = (spBase + 64);
          stackUtilSetLong(u0, s, top);
          sp = top + 1;
          pc = (pcBase + 64) + pushSize;
          if (pushSize == 0) {
            cost = 2L;
            step = 1;
          } else {
            cost = 3L;
            step = 1;
          }
        }
        // END copied from PushOperationV2.SingleByte.staticOperation
        // BEGIN copied from PushOperationV2.SingleLimb.staticOperation
        case COPIED_PUSH2_TO_8 -> {
          final int pushSize = opcode - PUSH_BASE;
          if (!((spBase + 128) + 1 <= maxItems)) {
            frame.setTopV2(sp);
            frame.setPC(pc);
            frame.setGasRemaining(gas);
            result = AbstractOperationV2.OVERFLOW_RESPONSE;
            break;
          }
          final int start = (pcBase + 128) + 1;
          final int end = start + pushSize;
          long u0 = UInt256.getLongBE(code, start, Math.min(end, code.length));
          if (end > code.length) {
            final int remainingSize = code.length - start;
            final int shift = (pushSize - remainingSize) * 8;
            u0 <<= shift;
          }
          final int top = (spBase + 128);
          stackUtilSetLong(u0, s, top);
          sp = top + 1;
          pc = (pcBase + 128) + pushSize;
          cost = 3L;
          step = 1;
        }
        // END copied from PushOperationV2.SingleLimb.staticOperation
        // BEGIN copied from DupOperationV2.staticOperation
        case COPIED_DUP -> {
          final int index = opcode - DupOperationV2.DUP_BASE;
          if (!((spBase + 192) >= index)) {
            frame.setTopV2(sp);
            frame.setPC(pc);
            frame.setGasRemaining(gas);
            result = AbstractOperationV2.UNDERFLOW_RESPONSE;
            break;
          }
          if (!((spBase + 192) + 1 <= maxItems)) {
            frame.setTopV2(sp);
            frame.setPC(pc);
            frame.setGasRemaining(gas);
            result = AbstractOperationV2.OVERFLOW_RESPONSE;
            break;
          }
          final int top = (spBase + 192);
          stackUtilCopyWord(s, top - index, top);
          sp = top + 1;
          cost = 3L;
          step = 1;
        }
        // END copied from DupOperationV2.staticOperation
        // BEGIN copied from SwapOperationV2.staticOperation
        case COPIED_SWAP -> {
          final int index = opcode - SwapOperationV2.SWAP_BASE;
          if (!((spBase + 256) >= index + 1)) {
            frame.setTopV2(sp);
            frame.setPC(pc);
            frame.setGasRemaining(gas);
            result = AbstractOperationV2.UNDERFLOW_RESPONSE;
            break;
          }
          final int top = (spBase + 256);
          stackUtilSwapWords(s, top - 1, top - 1 - index);
          cost = 3L;
          step = 1;
        }
        // END copied from SwapOperationV2.staticOperation
        // BEGIN copied from JumpDestOperationV2.JUMPDEST_SUCCESS
        case COPIED_JUMPDEST -> {
          cost = 1L;
          step = 1;
        }
        // END copied from JumpDestOperationV2.JUMPDEST_SUCCESS
        // BEGIN copied from PopOperationV2.staticOperation
        case COPIED_POP -> {
          if (!((spBase + 384) >= 1)) {
            frame.setTopV2(sp);
            frame.setPC(pc);
            frame.setGasRemaining(gas);
            result = AbstractOperationV2.UNDERFLOW_RESPONSE;
            break;
          }
          sp = (spBase + 384) - 1;
          cost = 2L;
          step = 1;
        }
        // END copied from PopOperationV2.staticOperation
        // BEGIN copied from AddOperationV2.staticOperation
        case COPIED_ADD -> {
          if (!((spBase + 448) >= 2)) {
            {
              sp = 0;
              frame.setTopV2(sp);
              frame.setPC(pc);
              frame.setGasRemaining(gas);
              result = AbstractOperationV2.UNDERFLOW_RESPONSE;
              break;
            }
          }
          long[] stack = s;
          int top = (spBase + 448);
          final int aOffset = (top - 1) << 2;
          final int bOffset = (top - 2) << 2;
          final UInt256 valueA =
              new UInt256(
                  stack[aOffset], stack[aOffset + 1], stack[aOffset + 2], stack[aOffset + 3]);
          final UInt256 valueB =
              new UInt256(
                  stack[bOffset], stack[bOffset + 1], stack[bOffset + 2], stack[bOffset + 3]);
          final UInt256 r = valueA.add(valueB);
          stack[bOffset] = r.u3();
          stack[bOffset + 1] = r.u2();
          stack[bOffset + 2] = r.u1();
          stack[bOffset + 3] = r.u0();
          sp = top - 1;
          cost = 3L;
          step = 1;
        }
        // END copied from AddOperationV2.staticOperation
        // BEGIN copied from IsZeroOperationV2.staticOperation
        case COPIED_ISZERO -> {
          if (!((spBase + 512) >= 1)) {
            frame.setTopV2(sp);
            frame.setPC(pc);
            frame.setGasRemaining(gas);
            result = AbstractOperationV2.UNDERFLOW_RESPONSE;
            break;
          }
          final long[] stack = s;
          final int top = (spBase + 512);
          stackUtilSetLong(stackUtilIsZeroAt(stack, top, 0) ? 1L : 0L, stack, top - 1);
          cost = 3L;
          step = 1;
        }
        // END copied from IsZeroOperationV2.staticOperation
        // BEGIN copied from AndOperationV2.staticOperation
        case COPIED_AND -> {
          if (!((spBase + 576) >= 2)) {
            {
              sp = 0;
              frame.setTopV2(sp);
              frame.setPC(pc);
              frame.setGasRemaining(gas);
              result = AbstractOperationV2.UNDERFLOW_RESPONSE;
              break;
            }
          }
          long[] stack = s;
          int top = (spBase + 576);
          final int aOffset = (top - 1) << 2;
          final int bOffset = (top - 2) << 2;
          final UInt256 valueA =
              new UInt256(
                  stack[aOffset], stack[aOffset + 1], stack[aOffset + 2], stack[aOffset + 3]);
          final UInt256 valueB =
              new UInt256(
                  stack[bOffset], stack[bOffset + 1], stack[bOffset + 2], stack[bOffset + 3]);
          final UInt256 r = valueA.and(valueB);
          stack[bOffset] = r.u3();
          stack[bOffset + 1] = r.u2();
          stack[bOffset + 2] = r.u1();
          stack[bOffset + 3] = r.u0();
          sp = top - 1;
          cost = 3L;
          step = 1;
        }
        // END copied from AndOperationV2.staticOperation
        // BEGIN copied from SubOperationV2.staticOperation
        case COPIED_SUB -> {
          if (!((spBase + 640) >= 2)) {
            {
              sp = 0;
              frame.setTopV2(sp);
              frame.setPC(pc);
              frame.setGasRemaining(gas);
              result = AbstractOperationV2.UNDERFLOW_RESPONSE;
              break;
            }
          }
          long[] stack = s;
          int top = (spBase + 640);
          final int aOffset = (top - 1) << 2;
          final int bOffset = (top - 2) << 2;
          final UInt256 valueA =
              new UInt256(
                  stack[aOffset], stack[aOffset + 1], stack[aOffset + 2], stack[aOffset + 3]);
          final UInt256 valueB =
              new UInt256(
                  stack[bOffset], stack[bOffset + 1], stack[bOffset + 2], stack[bOffset + 3]);
          final UInt256 r = valueA.sub(valueB);
          stack[bOffset] = r.u3();
          stack[bOffset + 1] = r.u2();
          stack[bOffset + 2] = r.u1();
          stack[bOffset + 3] = r.u0();
          sp = top - 1;
          cost = 3L;
          step = 1;
        }
        // END copied from SubOperationV2.staticOperation
        // BEGIN copied from LtOperationV2.staticOperation
        case COPIED_LT -> {
          if (!((spBase + 704) >= 2)) {
            {
              sp = 0;
              frame.setTopV2(sp);
              frame.setPC(pc);
              frame.setGasRemaining(gas);
              result = AbstractOperationV2.UNDERFLOW_RESPONSE;
              break;
            }
          }
          final long[] stack = s;
          final int top = (spBase + 704);
          final boolean lessThan =
              stackUtilCompareUnsigned(stack, (top - 1) << 2, (top - 2) << 2) < 0;
          stackUtilSetLong(lessThan ? 1L : 0L, stack, top - 2);
          sp = top - 1;
          cost = 3L;
          step = 1;
        }
        // END copied from LtOperationV2.staticOperation
        // BEGIN copied from ShlOperationV2.staticOperation
        case COPIED_SHL -> {
          if (constantinople) {
            {
              if (!((spBase + 768) >= 2)) {
                {
                  sp = 0;
                  frame.setTopV2(sp);
                  frame.setPC(pc);
                  frame.setGasRemaining(gas);
                  result = AbstractOperationV2.UNDERFLOW_RESPONSE;
                  break;
                }
              }
              long[] stack = s;
              int top = (spBase + 768);
              final int shiftOffset = (--top) << 2;
              final UInt256 shift =
                  new UInt256(
                      stack[shiftOffset],
                      stack[shiftOffset + 1],
                      stack[shiftOffset + 2],
                      stack[shiftOffset + 3]);
              final int valueOffset = (--top) << 2;
              final UInt256 value =
                  new UInt256(
                      stack[valueOffset],
                      stack[valueOffset + 1],
                      stack[valueOffset + 2],
                      stack[valueOffset + 3]);
              final UInt256 result2 = value.shl(shift);
              int resultOffset = top << 2;
              stack[resultOffset] = result2.u3();
              stack[resultOffset + 1] = result2.u2();
              stack[resultOffset + 2] = result2.u1();
              stack[resultOffset + 3] = result2.u0();
              sp = ++top;
              cost = 3L;
              step = 1;
            }
          } else {
            {
              frame.setTopV2(sp);
              frame.setPC(pc);
              frame.setGasRemaining(gas);
              result = new OperationResult(0, ExceptionalHaltReason.newInvalidOperation(opcode));
            }
          }
        }
        // END copied from ShlOperationV2.staticOperation
        // BEGIN copied from GtOperationV2.staticOperation
        case COPIED_GT -> {
          if (!((spBase + 832) >= 2)) {
            {
              sp = 0;
              frame.setTopV2(sp);
              frame.setPC(pc);
              frame.setGasRemaining(gas);
              result = AbstractOperationV2.UNDERFLOW_RESPONSE;
              break;
            }
          }
          final long[] stack = s;
          final int top = (spBase + 832);
          final boolean greaterThan =
              stackUtilCompareUnsigned(stack, (top - 1) << 2, (top - 2) << 2) > 0;
          stackUtilSetLong(greaterThan ? 1L : 0L, stack, top - 2);
          sp = top - 1;
          cost = 3L;
          step = 1;
        }
        // END copied from GtOperationV2.staticOperation
        // BEGIN copied from PushOperationV2.SingleByte.staticOperation
        case COPIED_PUSH0 -> {
          if (shanghai) {
            {
              int pushSize = 0;
              if (!((spBase + 896) + 1 <= maxItems)) {
                frame.setTopV2(sp);
                frame.setPC(pc);
                frame.setGasRemaining(gas);
                result = AbstractOperationV2.OVERFLOW_RESPONSE;
                break;
              }
              long u0 = 0;
              final int start = (pcBase + 896) + 1;
              if (pushSize != 0 && start < code.length) {
                u0 = code[start] & 0xFFL;
              }
              final int top = (spBase + 896);
              stackUtilSetLong(u0, s, top);
              sp = top + 1;
              pc = (pcBase + 896) + pushSize;
              if (pushSize == 0) {
                cost = 2L;
                step = 1;
              } else {
                cost = 3L;
                step = 1;
              }
            }
          } else {
            {
              frame.setTopV2(sp);
              frame.setPC(pc);
              frame.setGasRemaining(gas);
              result = new OperationResult(0, ExceptionalHaltReason.newInvalidOperation(opcode));
            }
          }
        }
        // END copied from PushOperationV2.SingleByte.staticOperation
        // BEGIN copied from ShrOperationV2.staticOperation
        case COPIED_SHR -> {
          if (constantinople) {
            {
              if (!((spBase + 960) >= 2)) {
                {
                  sp = 0;
                  frame.setTopV2(sp);
                  frame.setPC(pc);
                  frame.setGasRemaining(gas);
                  result = AbstractOperationV2.UNDERFLOW_RESPONSE;
                  break;
                }
              }
              long[] stack = s;
              int top = (spBase + 960);
              final int shiftOffset = (--top) << 2;
              final UInt256 shift =
                  new UInt256(
                      stack[shiftOffset],
                      stack[shiftOffset + 1],
                      stack[shiftOffset + 2],
                      stack[shiftOffset + 3]);
              final int valueOffset = (--top) << 2;
              final UInt256 value =
                  new UInt256(
                      stack[valueOffset],
                      stack[valueOffset + 1],
                      stack[valueOffset + 2],
                      stack[valueOffset + 3]);
              final UInt256 result2 = value.shr(shift);
              int resultOffset = top << 2;
              stack[resultOffset] = result2.u3();
              stack[resultOffset + 1] = result2.u2();
              stack[resultOffset + 2] = result2.u1();
              stack[resultOffset + 3] = result2.u0();
              sp = ++top;
              cost = 3L;
              step = 1;
            }
          } else {
            {
              frame.setTopV2(sp);
              frame.setPC(pc);
              frame.setGasRemaining(gas);
              result = new OperationResult(0, ExceptionalHaltReason.newInvalidOperation(opcode));
            }
          }
        }
        // END copied from ShrOperationV2.staticOperation
        // BEGIN copied from OrOperationV2.staticOperation
        case COPIED_OR -> {
          if (!((spBase + 1024) >= 2)) {
            {
              sp = 0;
              frame.setTopV2(sp);
              frame.setPC(pc);
              frame.setGasRemaining(gas);
              result = AbstractOperationV2.UNDERFLOW_RESPONSE;
              break;
            }
          }
          long[] stack = s;
          int top = (spBase + 1024);
          final int aOffset = (top - 1) << 2;
          final int bOffset = (top - 2) << 2;
          final UInt256 valueA =
              new UInt256(
                  stack[aOffset], stack[aOffset + 1], stack[aOffset + 2], stack[aOffset + 3]);
          final UInt256 valueB =
              new UInt256(
                  stack[bOffset], stack[bOffset + 1], stack[bOffset + 2], stack[bOffset + 3]);
          final UInt256 r = valueA.or(valueB);
          stack[bOffset] = r.u3();
          stack[bOffset + 1] = r.u2();
          stack[bOffset + 2] = r.u1();
          stack[bOffset + 3] = r.u0();
          sp = top - 1;
          cost = 3L;
          step = 1;
        }
        // END copied from OrOperationV2.staticOperation
        // BEGIN copied from NotOperationV2.staticOperation
        case COPIED_NOT -> {
          if (!((spBase + 1088) >= 1)) {
            frame.setTopV2(sp);
            frame.setPC(pc);
            frame.setGasRemaining(gas);
            result = AbstractOperationV2.UNDERFLOW_RESPONSE;
            break;
          }
          long[] stack = s;
          final int aOffset = ((spBase + 1088) - 1) << 2;
          final UInt256 valueA =
              new UInt256(
                  stack[aOffset], stack[aOffset + 1], stack[aOffset + 2], stack[aOffset + 3]);
          final UInt256 r = valueA.not();
          stack[aOffset] = r.u3();
          stack[aOffset + 1] = r.u2();
          stack[aOffset + 2] = r.u1();
          stack[aOffset + 3] = r.u0();
          cost = 3L;
          step = 1;
        }
        // END copied from NotOperationV2.staticOperation
        // BEGIN copied from SLtOperationV2.staticOperation
        case COPIED_SLT -> {
          if (!((spBase + 1152) >= 2)) {
            {
              sp = 0;
              frame.setTopV2(sp);
              frame.setPC(pc);
              frame.setGasRemaining(gas);
              result = AbstractOperationV2.UNDERFLOW_RESPONSE;
              break;
            }
          }
          final long[] stack = s;
          final int top = (spBase + 1152);
          final boolean lessThan =
              stackUtilCompareSigned(stack, (top - 1) << 2, (top - 2) << 2) < 0;
          stackUtilSetLong(lessThan ? 1L : 0L, stack, top - 2);
          sp = top - 1;
          cost = 3L;
          step = 1;
        }
        // END copied from SLtOperationV2.staticOperation
        // END GENERATED cases
        default -> {
          // everything else goes through executeOperationV2 below
        }
      }
      if (cost >= 0) {
        if (gas < cost) {
          haltOutOfGas(frame, sp, pc, gas - cost);
          return;
        }
        gas -= cost;
        pc += step;
        continue;
      }
      if (result == null) {
        frame.setTopV2(sp);
        frame.setPC(pc);
        frame.setGasRemaining(gas);
        result = executeOperationV2(frame, code, pc, opcode);
      }
      completeOperation(frame, result);
      if (frame.getState() != State.CODE_EXECUTING) {
        return;
      }
      sp = frame.stackTopV2();
      pc = frame.getPC();
      gas = frame.getRemainingGas();
    }
  }

  /** Halts the frame as {@link #completeOperation} does for an operation it cannot pay for. */
  private static void haltOutOfGas(
      final MessageFrame frame, final int sp, final int pc, final long gasLeft) {
    frame.setTopV2(sp);
    frame.setPC(pc);
    frame.setGasRemaining(gasLeft);
    frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    frame.setState(State.EXCEPTIONAL_HALT);
  }

  /**
   * The v2 interpreter loop for traced execution, which calls the tracer around every operation.
   */
  private void runToHaltV2Traced(final MessageFrame frame, final OperationTracer operationTracer) {
    final byte[] code = frame.getCode().getBytes().toArrayUnsafe();
    final Operation[] operationArray = operations.getOperations();
    while (frame.getState() == State.CODE_EXECUTING) {
      final int pc = frame.getPC();
      final int opcode = pc < code.length ? code[pc] & 0xff : 0;
      frame.setCurrentOperation(pc < code.length ? operationArray[opcode] : endOfScriptStop);
      operationTracer.tracePreExecution(frame);
      final OperationResult result = executeOperationV2(frame, code, pc, opcode);
      completeOperation(frame, result);
      operationTracer.tracePostExecution(frame, result);
    }
  }

  /**
   * Executes one operation on the v2 stack.
   *
   * @param frame the frame
   * @param code the code being executed
   * @param pc the program counter
   * @param opcode the operation code, 0 past the end of the code
   * @return the result of the operation
   */
  private OperationResult executeOperationV2(
      final MessageFrame frame, final byte[] code, final int pc, final int opcode) {
    try {
      return switch (opcode) {
        case 0x00 -> StopOperationV2.staticOperation(frame);
        case 0x01 -> AddOperationV2.staticOperation(frame);
        case 0x02 -> MulOperationV2.staticOperation(frame);
        case 0x03 -> SubOperationV2.staticOperation(frame);
        case 0x04 -> DivOperationV2.staticOperation(frame);
        case 0x05 -> SDivOperationV2.staticOperation(frame);
        case 0x06 -> ModOperationV2.staticOperation(frame);
        case 0x07 -> SModOperationV2.staticOperation(frame);
        case 0x08 -> AddModOperationV2.staticOperation(frame);
        case 0x09 -> MulModOperationV2.staticOperation(frame);
        case 0x0a -> ExpOperationV2.staticOperation(frame, gasCalculator);
        case 0x0b -> SignExtendOperationV2.staticOperation(frame);
        case 0x0c, 0x0d, 0x0e, 0x0f -> InvalidOperationV2.invalidOperationResult(opcode);
        case 0x10 -> LtOperationV2.staticOperation(frame);
        case 0x11 -> GtOperationV2.staticOperation(frame);
        case 0x12 -> SLtOperationV2.staticOperation(frame);
        case 0x13 -> SGtOperationV2.staticOperation(frame);
        case 0x14 -> operationsV2[opcode].execute(frame, this);
        case 0x15 -> IsZeroOperationV2.staticOperation(frame);
        case 0x16 -> AndOperationV2.staticOperation(frame);
        case 0x17 -> OrOperationV2.staticOperation(frame);
        case 0x18 -> XorOperationV2.staticOperation(frame);
        case 0x19 -> NotOperationV2.staticOperation(frame);
        case 0x1a -> ByteOperationV2.staticOperation(frame);
        case 0x1b ->
            enableConstantinople
                ? ShlOperationV2.staticOperation(frame)
                : InvalidOperationV2.invalidOperationResult(opcode);
        case 0x1c ->
            enableConstantinople
                ? ShrOperationV2.staticOperation(frame)
                : InvalidOperationV2.invalidOperationResult(opcode);
        case 0x1d ->
            enableConstantinople
                ? SarOperationV2.staticOperation(frame)
                : InvalidOperationV2.invalidOperationResult(opcode);
        case 0x1e ->
            enableOsaka
                ? CountLeadingZerosOperationV2.staticOperation(frame)
                : InvalidOperationV2.invalidOperationResult(opcode);
        case 0x50 -> PopOperationV2.staticOperation(frame);
        case 0x56 -> JumpOperationV2.staticOperation(frame);
        case 0x57 -> JumpiOperationV2.staticOperation(frame);
        case 0x5b -> JumpDestOperationV2.JUMPDEST_SUCCESS;
        case 0x5F -> // PUSH0
            enableShanghai
                ? PushOperationV2.SingleByte.staticOperation(frame, code, pc, 0)
                : InvalidOperationV2.invalidOperationResult(opcode);
        case 0x60 -> // PUSH1
            PushOperationV2.SingleByte.staticOperation(frame, code, pc, 1);
        case 0x61, // PUSH2-8
            0x62,
            0x63,
            0x64,
            0x65,
            0x66,
            0x67 ->
            PushOperationV2.SingleLimb.staticOperation(frame, code, pc, opcode - PUSH_BASE);
        case 0x68, // PUSH9-32
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
            0x7f ->
            PushOperationV2.MultiLimb.staticOperation(frame, code, pc, opcode - PUSH_BASE);
        case 0x80, // DUP1-16
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
            0x8f ->
            DupOperationV2.staticOperation(frame, opcode - DupOperationV2.DUP_BASE);
        case 0x90, // SWAP1-16
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
            0x9f ->
            SwapOperationV2.staticOperation(frame, opcode - SwapOperationV2.SWAP_BASE);
        case 0xe6 -> // DUPN (EIP-8024)
            enableAmsterdam
                ? DupNOperationV2.staticOperation(frame, code, pc)
                : InvalidOperationV2.invalidOperationResult(opcode);
        case 0xe7 -> // SWAPN (EIP-8024)
            enableAmsterdam
                ? SwapNOperationV2.staticOperation(frame, code, pc)
                : InvalidOperationV2.invalidOperationResult(opcode);
        case 0xe8 -> // EXCHANGE (EIP-8024)
            enableAmsterdam
                ? ExchangeOperationV2.staticOperation(frame, code, pc)
                : InvalidOperationV2.invalidOperationResult(opcode);
        default -> operationsV2[opcode].execute(frame, this);
      };
    } catch (final OverflowException oe) {
      return OVERFLOW_RESPONSE;
    } catch (final UnderflowException ue) {
      return UNDERFLOW_RESPONSE;
    }
  }

  /**
   * Charges an operation's gas and moves past it, or halts the frame if the operation failed.
   *
   * @param frame the frame
   * @param result the result of the operation
   */
  private static void completeOperation(final MessageFrame frame, final OperationResult result) {
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
  }

  /**
   * Get Operations (unsafe)
   *
   * @return Operations array
   */
  public Operation[] getOperationsUnsafe() {
    return operations.getOperations();
  }

  private OperationResult shiftOperation(
      final MessageFrame frame,
      final Function<MessageFrame, OperationResult> standard,
      final Function<MessageFrame, OperationResult> optimized) {
    return evmConfiguration.enableOptimizedOpcodes()
        ? optimized.apply(frame)
        : standard.apply(frame);
  }

  /**
   * Gets or creates code instance with a cached jump destination.
   *
   * @param codeHash the code hash
   * @param codeBytes the code bytes
   * @return the code instance with the cached jump destination
   */
  public Code getOrCreateCachedJumpDest(final Hash codeHash, final Bytes codeBytes) {
    if (Hash.EMPTY.getBytes().equals(codeHash.getBytes())) {
      return Code.EMPTY_CODE;
    }

    Code result = jumpDestOnlyCodeCache.getIfPresent(codeHash);
    if (result == null) {
      result = new Code(codeBytes);
      jumpDestOnlyCodeCache.put(codeHash, result);
    }

    return result;
  }
}
