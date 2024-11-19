/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet;

import static org.hyperledger.besu.evm.frame.MessageFrame.DEFAULT_MAX_STACK_SIZE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Deque;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemCallProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(SystemCallProcessor.class);

  /** The system address */
  static final Address SYSTEM_ADDRESS =
      Address.fromHexString("0xfffffffffffffffffffffffffffffffffffffffe");

  private final MainnetTransactionProcessor mainnetTransactionProcessor;

  public SystemCallProcessor(final MainnetTransactionProcessor mainnetTransactionProcessor) {
    this.mainnetTransactionProcessor = mainnetTransactionProcessor;
  }

  /**
   * Processes a system call to a specified address, using the provided world state, block header,
   * operation tracer, and block hash lookup.
   *
   * @param callAddress the address to call.
   * @param worldState the current world state.
   * @param blockHeader the current block header.
   * @param operationTracer the operation tracer for tracing EVM operations.
   * @param blockHashLookup the block hash lookup function.
   * @return the output data from the call. If no code exists at the callAddress then an empty Bytes
   *     is returned.
   */
  public Bytes process(
      final Address callAddress,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup) {

    // if no code exists at CALL_ADDRESS, the call must fail silently
    final Account maybeContract = worldState.get(callAddress);
    if (maybeContract == null) {
      LOG.trace("System call address not found {}", callAddress);
      return Bytes.EMPTY;
    }

    final AbstractMessageProcessor messageProcessor =
        mainnetTransactionProcessor.getMessageProcessor(MessageFrame.Type.MESSAGE_CALL);
    final MessageFrame initialFrame =
        createCallFrame(callAddress, worldState, blockHeader, blockHashLookup);

    return processFrame(initialFrame, messageProcessor, operationTracer, worldState);
  }

  private Bytes processFrame(
      final MessageFrame frame,
      final AbstractMessageProcessor processor,
      final OperationTracer tracer,
      final WorldUpdater updater) {

    if (!frame.getCode().isValid()) {
      throw new RuntimeException("System call did not execute to completion - opcode invalid");
    }

    Deque<MessageFrame> stack = frame.getMessageFrameStack();
    while (!stack.isEmpty()) {
      processor.process(stack.peekFirst(), tracer);
    }

    if (frame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      updater.commit();
      return frame.getOutputData();
    }

    // the call must execute to completion
    throw new RuntimeException("System call did not execute to completion");
  }

  private MessageFrame createCallFrame(
      final Address callAddress,
      final WorldUpdater worldUpdater,
      final ProcessableBlockHeader blockHeader,
      final BlockHashLookup blockHashLookup) {

    final Optional<Account> maybeContract = Optional.ofNullable(worldUpdater.get(callAddress));
    final AbstractMessageProcessor processor =
        mainnetTransactionProcessor.getMessageProcessor(MessageFrame.Type.MESSAGE_CALL);

    return MessageFrame.builder()
        .maxStackSize(DEFAULT_MAX_STACK_SIZE)
        .worldUpdater(worldUpdater)
        .initialGas(30_000_000L)
        .originator(SYSTEM_ADDRESS)
        .gasPrice(Wei.ZERO)
        .blobGasPrice(Wei.ZERO)
        .value(Wei.ZERO)
        .apparentValue(Wei.ZERO)
        .blockValues(blockHeader)
        .completer(__ -> {})
        .miningBeneficiary(Address.ZERO) // Confirm this
        .type(MessageFrame.Type.MESSAGE_CALL)
        .address(callAddress)
        .contract(callAddress)
        .inputData(Bytes.EMPTY)
        .sender(SYSTEM_ADDRESS)
        .blockHashLookup(blockHashLookup)
        .code(
            maybeContract
                .map(c -> processor.getCodeFromEVM(c.getCodeHash(), c.getCode()))
                .orElse(CodeV0.EMPTY_CODE))
        .build();
  }
}
