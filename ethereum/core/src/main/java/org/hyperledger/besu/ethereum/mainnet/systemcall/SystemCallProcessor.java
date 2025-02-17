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
package org.hyperledger.besu.ethereum.mainnet.systemcall;

import static org.hyperledger.besu.evm.frame.MessageFrame.DEFAULT_MAX_STACK_SIZE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
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
   * Processes a system call.
   *
   * @param callAddress The address to call.
   * @param context The system call context. The input data to the system call.
   * @param inputData The input data to the system call.
   * @return The output of the system call.
   */
  public Bytes process(
      final Address callAddress, final BlockProcessingContext context, final Bytes inputData) {
    WorldUpdater updater = context.getWorldState().updater();

    // if no code exists at CALL_ADDRESS, the call must fail silently
    final Account maybeContract = updater.get(callAddress);
    if (maybeContract == null) {
      LOG.trace("System call address not found {}", callAddress);
      return Bytes.EMPTY;
    }

    final AbstractMessageProcessor processor =
        mainnetTransactionProcessor.getMessageProcessor(MessageFrame.Type.MESSAGE_CALL);
    final MessageFrame frame =
        createMessageFrame(
            callAddress,
            updater,
            context.getBlockHeader(),
            context.getBlockHashLookup(),
            inputData);

    if (!frame.getCode().isValid()) {
      throw new RuntimeException("System call did not execute to completion - opcode invalid");
    }

    Deque<MessageFrame> stack = frame.getMessageFrameStack();
    while (!stack.isEmpty()) {
      processor.process(stack.peekFirst(), context.getOperationTracer());
    }

    if (frame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      updater.commit();
      return frame.getOutputData();
    }

    // the call must execute to completion
    throw new RuntimeException("System call did not execute to completion");
  }

  private MessageFrame createMessageFrame(
      final Address callAddress,
      final WorldUpdater worldUpdater,
      final ProcessableBlockHeader blockHeader,
      final BlockHashLookup blockHashLookup,
      final Bytes inputData) {

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
        .inputData(inputData)
        .sender(SYSTEM_ADDRESS)
        .blockHashLookup(blockHashLookup)
        .code(
            maybeContract
                .map(c -> processor.getCodeFromEVM(c.getCodeHash(), c.getCode()))
                .orElse(CodeV0.EMPTY_CODE))
        .build();
  }
}
