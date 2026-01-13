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

import static com.google.common.base.Preconditions.checkNotNull;
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
import org.hyperledger.besu.evm.operation.DupOperation;
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
import org.hyperledger.besu.evm.operation.NotOperation;
import org.hyperledger.besu.evm.operation.NotOperationOptimized;
import org.hyperledger.besu.evm.operation.OpcodeExecutor;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.operation.OrOperation;
import org.hyperledger.besu.evm.operation.OrOperationOptimized;
import org.hyperledger.besu.evm.operation.PopOperation;
import org.hyperledger.besu.evm.operation.Push0Operation;
import org.hyperledger.besu.evm.operation.PushOperation;
import org.hyperledger.besu.evm.operation.SDivOperation;
import org.hyperledger.besu.evm.operation.SGtOperation;
import org.hyperledger.besu.evm.operation.SLtOperation;
import org.hyperledger.besu.evm.operation.SModOperation;
import org.hyperledger.besu.evm.operation.SModOperationOptimized;
import org.hyperledger.besu.evm.operation.SignExtendOperation;
import org.hyperledger.besu.evm.operation.StopOperation;
import org.hyperledger.besu.evm.operation.SubOperation;
import org.hyperledger.besu.evm.operation.SwapOperation;
import org.hyperledger.besu.evm.operation.VirtualOperation;
import org.hyperledger.besu.evm.operation.XorOperation;
import org.hyperledger.besu.evm.operation.XorOperationOptimized;
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
  private final EvmConfiguration evmConfiguration;
  private final EvmSpecVersion evmSpecVersion;

  // Optimized operation flags
  private final boolean enableShanghai;
  private final boolean enableOsaka;

  private final JumpDestOnlyCodeCache jumpDestOnlyCodeCache;

  // Pre-built opcode executor array for fast dispatch (avoids giant switch statement)
  private final OpcodeExecutor[] executors = new OpcodeExecutor[256];

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

    enableShanghai = EvmSpecVersion.SHANGHAI.ordinal() <= evmSpecVersion.ordinal();
    enableOsaka = EvmSpecVersion.OSAKA.ordinal() <= evmSpecVersion.ordinal();

    // Build the executor array once at construction time
    buildExecutorArray();
  }

  /**
   * Builds the opcode executor array once at EVM construction time. This converts the runtime
   * switch statement dispatch into a pre-computed array of function references, eliminating branch
   * prediction overhead for the giant switch.
   */
  @SuppressWarnings("MutablePublicArray")
  private void buildExecutorArray() {
    final boolean useOptimized = evmConfiguration.enableOptimizedOpcodes();
    final Operation[] ops = operations.getOperations();

    // Arithmetic operations (0x00 - 0x0b)
    executors[0x00] = (f, c, p, e) -> StopOperation.staticOperation(f);
    executors[0x01] =
        useOptimized
            ? (f, c, p, e) -> AddOperationOptimized.staticOperation(f)
            : (f, c, p, e) -> AddOperation.staticOperation(f);
    executors[0x02] = (f, c, p, e) -> MulOperation.staticOperation(f);
    executors[0x03] = (f, c, p, e) -> SubOperation.staticOperation(f);
    executors[0x04] = (f, c, p, e) -> DivOperation.staticOperation(f);
    executors[0x05] = (f, c, p, e) -> SDivOperation.staticOperation(f);
    executors[0x06] =
        useOptimized
            ? (f, c, p, e) -> ModOperationOptimized.staticOperation(f)
            : (f, c, p, e) -> ModOperation.staticOperation(f);
    executors[0x07] =
        useOptimized
            ? (f, c, p, e) -> SModOperationOptimized.staticOperation(f)
            : (f, c, p, e) -> SModOperation.staticOperation(f);
    executors[0x08] =
        useOptimized
            ? (f, c, p, e) -> AddModOperationOptimized.staticOperation(f)
            : (f, c, p, e) -> AddModOperation.staticOperation(f);
    executors[0x09] =
        useOptimized
            ? (f, c, p, e) -> MulModOperationOptimized.staticOperation(f)
            : (f, c, p, e) -> MulModOperation.staticOperation(f);
    executors[0x0a] = (f, c, p, e) -> ExpOperation.staticOperation(f, gasCalculator);
    executors[0x0b] = (f, c, p, e) -> SignExtendOperation.staticOperation(f);

    // Invalid opcodes 0x0c-0x0f
    for (int i = 0x0c; i <= 0x0f; i++) {
      final int opcode = i;
      executors[i] = (f, c, p, e) -> InvalidOperation.invalidOperationResult(opcode);
    }

    // Comparison operations (0x10 - 0x1a)
    executors[0x10] = (f, c, p, e) -> LtOperation.staticOperation(f);
    executors[0x11] = (f, c, p, e) -> GtOperation.staticOperation(f);
    executors[0x12] = (f, c, p, e) -> SLtOperation.staticOperation(f);
    executors[0x13] = (f, c, p, e) -> SGtOperation.staticOperation(f);
    // 0x14 EQ - use fallback
    executors[0x15] = (f, c, p, e) -> IsZeroOperation.staticOperation(f);
    executors[0x16] =
        useOptimized
            ? (f, c, p, e) -> AndOperationOptimized.staticOperation(f)
            : (f, c, p, e) -> AndOperation.staticOperation(f);
    executors[0x17] =
        useOptimized
            ? (f, c, p, e) -> OrOperationOptimized.staticOperation(f)
            : (f, c, p, e) -> OrOperation.staticOperation(f);
    executors[0x18] =
        useOptimized
            ? (f, c, p, e) -> XorOperationOptimized.staticOperation(f)
            : (f, c, p, e) -> XorOperation.staticOperation(f);
    executors[0x19] =
        useOptimized
            ? (f, c, p, e) -> NotOperationOptimized.staticOperation(f)
            : (f, c, p, e) -> NotOperation.staticOperation(f);
    executors[0x1a] = (f, c, p, e) -> ByteOperation.staticOperation(f);

    // CLZ (EIP-7939, Osaka)
    executors[0x1e] =
        enableOsaka
            ? (f, c, p, e) -> CountLeadingZerosOperation.staticOperation(f)
            : (f, c, p, e) -> InvalidOperation.invalidOperationResult(0x1e);

    // Stack operations
    executors[0x50] = (f, c, p, e) -> PopOperation.staticOperation(f);

    // Jump operations
    executors[0x56] = (f, c, p, e) -> JumpOperation.staticOperation(f);
    executors[0x57] = (f, c, p, e) -> JumpiOperation.staticOperation(f);
    executors[0x5b] = (f, c, p, e) -> JumpDestOperation.JUMPDEST_SUCCESS;

    // PUSH0 (EIP-3855, Shanghai)
    executors[0x5f] =
        enableShanghai
            ? (f, c, p, e) -> Push0Operation.staticOperation(f)
            : (f, c, p, e) -> InvalidOperation.invalidOperationResult(0x5f);

    // PUSH1-32 (0x60-0x7f)
    for (int i = 0x60; i <= 0x7f; i++) {
      final int pushSize = i - PUSH_BASE;
      executors[i] = (f, c, p, e) -> PushOperation.staticOperation(f, c, p, pushSize);
    }

    // DUP1-16 (0x80-0x8f)
    for (int i = 0x80; i <= 0x8f; i++) {
      final int dupIndex = i - DupOperation.DUP_BASE;
      executors[i] = (f, c, p, e) -> DupOperation.staticOperation(f, dupIndex);
    }

    // SWAP1-16 (0x90-0x9f)
    for (int i = 0x90; i <= 0x9f; i++) {
      final int swapIndex = i - SWAP_BASE;
      executors[i] = (f, c, p, e) -> SwapOperation.staticOperation(f, swapIndex);
    }

    // EIP-8024 operations (Amsterdam) - DUPN (0xe6), SWAPN (0xe7), EXCHANGE (0xe8)
    // These will use the Operation instance fallback when the operations are registered,
    // or return invalid opcode if not enabled for this fork.
    // Static dispatch can be added once EIP-8024 operations are implemented.

    // Fill remaining null slots with Operation instance fallback
    for (int i = 0; i < 256; i++) {
      if (executors[i] == null) {
        final Operation op = ops[i];
        executors[i] = (f, c, p, e) -> op.execute(f, e);
      }
    }
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
   * @param tracing the tracing
   */
  // Note to maintainers: lots of Java idioms and OO principals are being set aside in the
  // name of performance. This is one of the hottest sections of code.
  //
  // The giant switch statement has been replaced with a pre-built executor array
  // that is populated once at EVM construction time. This eliminates branch prediction
  // overhead and moves configuration checks (optimized opcodes, fork flags) out of the hot loop.
  //
  // Please benchmark before refactoring.
  public void runToHalt(final MessageFrame frame, final OperationTracer tracing) {
    evmSpecVersion.maybeWarnVersion();

    var operationTracer = tracing == OperationTracer.NO_TRACING ? null : tracing;
    byte[] code = frame.getCode().getBytes().toArrayUnsafe();
    Operation[] operationArray = operations.getOperations();
    OpcodeExecutor[] exec = this.executors; // Local ref for speed

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
        // Array dispatch replaces the giant switch statement
        result = exec[opcode].execute(frame, code, pc, this);
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
   * Gets or creates code instance with a cached jump destination.
   *
   * @param codeHash the code hash
   * @param codeBytes the code bytes
   * @return the code instance with the cached jump destination
   */
  public Code getOrCreateCachedJumpDest(final Hash codeHash, final Bytes codeBytes) {
    checkNotNull(codeHash);

    Code result = jumpDestOnlyCodeCache.getIfPresent(codeHash);
    if (result == null) {
      result = new Code(codeBytes);
      jumpDestOnlyCodeCache.put(codeHash, result);
    }

    return result;
  }
}
