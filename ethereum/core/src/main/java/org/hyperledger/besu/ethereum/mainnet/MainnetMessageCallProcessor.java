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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.ModificationNotAllowedException;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;

import java.util.Collection;

import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class MainnetMessageCallProcessor extends AbstractMessageProcessor {
  private static final Logger LOG = LogManager.getLogger();

  private final PrecompileContractRegistry precompiles;

  public MainnetMessageCallProcessor(
      final EVM evm,
      final PrecompileContractRegistry precompiles,
      final Collection<Address> forceCommitAddresses) {
    super(evm, forceCommitAddresses);
    this.precompiles = precompiles;
  }

  public MainnetMessageCallProcessor(final EVM evm, final PrecompileContractRegistry precompiles) {
    super(evm, ImmutableSet.of());
    this.precompiles = precompiles;
  }

  @Override
  public void start(final MessageFrame frame, final OperationTracer operationTracer) {
    LOG.trace("Executing message-call");
    try {
      transferValue(frame);

      // Check first if the message call is to a pre-compile contract
      final PrecompiledContract precompile =
          precompiles.get(frame.getContractAddress(), frame.getContractAccountVersion());
      if (precompile != null) {
        executePrecompile(precompile, frame, operationTracer);
      } else {
        frame.setState(MessageFrame.State.CODE_EXECUTING);
      }
    } catch (ModificationNotAllowedException ex) {
      LOG.trace("Message call error: illegal modification not allowed from private state");
      frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
    }
  }

  @Override
  protected void codeSuccess(final MessageFrame frame, final OperationTracer operationTracer) {
    LOG.trace(
        "Successful message call of {} to {} (Gas remaining: {})",
        frame.getSenderAddress(),
        frame.getRecipientAddress(),
        frame.getRemainingGas());
    frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
  }

  /**
   * Transfers the message call value from the sender to the recipient.
   *
   * <p>Assumes that the transaction has been validated so that the sender has the required fund as
   * of the world state of this executor.
   */
  private void transferValue(final MessageFrame frame) {
    final MutableAccount senderAccount =
        frame.getWorldState().getOrCreateSenderAccount(frame.getSenderAddress()).getMutable();
    // The yellow paper explicitly states that if the recipient account doesn't exist at this
    // point, it is created.
    final MutableAccount recipientAccount =
        frame.getWorldState().getOrCreate(frame.getRecipientAddress()).getMutable();

    if (frame.getRecipientAddress().equals(frame.getSenderAddress())) {
      LOG.trace("Message call of {} to itself: no fund transferred", frame.getSenderAddress());
    } else {
      final Wei prevSenderBalance = senderAccount.decrementBalance(frame.getValue());
      final Wei prevRecipientBalance = recipientAccount.incrementBalance(frame.getValue());

      LOG.trace(
          "Transferred value {} for message call from {} ({} -> {}) to {} ({} -> {})",
          frame.getValue(),
          frame.getSenderAddress(),
          prevSenderBalance,
          senderAccount.getBalance(),
          frame.getRecipientAddress(),
          prevRecipientBalance,
          recipientAccount.getBalance());
    }
  }

  /**
   * Executes this message call knowing that it is a call to the provide pre-compiled contract.
   *
   * @param contract The contract this is a message call to.
   */
  private void executePrecompile(
      final PrecompiledContract contract,
      final MessageFrame frame,
      final OperationTracer operationTracer) {
    final Gas gasRequirement = contract.gasRequirement(frame.getInputData());
    if (frame.getRemainingGas().compareTo(gasRequirement) < 0) {
      LOG.trace(
          "Not enough gas available for pre-compiled contract code {}: requiring "
              + "{} but only {} gas available",
          contract,
          gasRequirement,
          frame.getRemainingGas());
      frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
    } else {
      frame.decrementRemainingGas(gasRequirement);
      final Bytes output = contract.compute(frame.getInputData(), frame);
      operationTracer.tracePrecompileCall(frame, gasRequirement, output);
      if (output != null) {
        if (contract.getName().equals("Privacy") || contract.getName().equals("OnChainPrivacy")) {
          // do not decrement the gas requirement for a privacy pre-compile contract call -> leads
          // to discrepancies in receipts root between public and private nodes in a network.
          frame.incrementRemainingGas(gasRequirement);
          frame.setState(MessageFrame.State.CODE_EXECUTING);
          return;
        }
        frame.setOutputData(output);
        LOG.trace(
            "Precompiled contract {}  successfully executed (gas consumed: {})",
            contract.getName(),
            gasRequirement);
        frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
      } else {
        LOG.trace("Precompiled contract  {} failed (gas consumed: {})", contract, gasRequirement);
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
      }
    }
  }
}
