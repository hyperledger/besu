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
package org.hyperledger.besu.evm.processor;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountState;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;

/**
 * A skeletal class for instantiating message processors.
 *
 * <p>The following methods have been created to be invoked when the message state changes via the
 * {@link MessageFrame.State}. Note that some of these methods are abstract while others have
 * default behaviors. There is currently no method for responding to a {@link
 * MessageFrame.State#CODE_SUSPENDED}.
 *
 * <table>
 * <caption>Method Overview</caption>
 * <tr>
 * <td><b>{@code MessageFrame.State}</b></td>
 * <td><b>Method</b></td>
 * </tr>
 * <tr>
 * <td>{@link MessageFrame.State#NOT_STARTED}</td>
 * <td>{@link AbstractMessageProcessor#start(MessageFrame, OperationTracer)}</td>
 * </tr>
 * <tr>
 * <td>{@link MessageFrame.State#CODE_EXECUTING}</td>
 * <td>{@link AbstractMessageProcessor#codeExecute(MessageFrame, OperationTracer)}</td>
 * </tr>
 * <tr>
 * <td>{@link MessageFrame.State#CODE_SUCCESS}</td>
 * <td>{@link AbstractMessageProcessor#codeSuccess(MessageFrame, OperationTracer)}</td>
 * </tr>
 * <tr>
 * <td>{@link MessageFrame.State#COMPLETED_FAILED}</td>
 * <td>{@link AbstractMessageProcessor#completedFailed(MessageFrame)}</td>
 * <tr>
 * <td>{@link MessageFrame.State#COMPLETED_SUCCESS}</td>
 * <td>{@link AbstractMessageProcessor#completedSuccess(MessageFrame)}</td>
 * </tr>
 * </table>
 */
public abstract class AbstractMessageProcessor {

  // List of addresses to force delete when they are touched but empty
  // when the state changes in the message are were not meant to be committed.
  private final Collection<? super Address> forceDeleteAccountsWhenEmpty;
  private final EVM evm;

  AbstractMessageProcessor(final EVM evm, final Collection<Address> forceDeleteAccountsWhenEmpty) {
    this.evm = evm;
    this.forceDeleteAccountsWhenEmpty = forceDeleteAccountsWhenEmpty;
  }

  protected abstract void start(MessageFrame frame, final OperationTracer operationTracer);

  /**
   * Gets called when the message frame code executes successfully.
   *
   * @param frame The message frame
   * @param operationTracer The tracer recording execution
   */
  protected abstract void codeSuccess(MessageFrame frame, final OperationTracer operationTracer);

  private void clearAccumulatedStateBesidesGasAndOutput(final MessageFrame frame) {
    ArrayList<Address> addresses =
        frame.getWorldUpdater().getTouchedAccounts().stream()
            .filter(AccountState::isEmpty)
            .map(Account::getAddress)
            .filter(forceDeleteAccountsWhenEmpty::contains)
            .collect(Collectors.toCollection(ArrayList::new));

    // Clear any pending changes.
    frame.getWorldUpdater().revert();

    // Force delete any requested accounts and commit the changes.
    ((Collection<Address>) addresses).forEach(h -> frame.getWorldUpdater().deleteAccount(h));
    frame.getWorldUpdater().commit();

    frame.clearLogs();
    frame.clearSelfDestructs();
    frame.clearGasRefund();
  }

  /**
   * Gets called when the message frame encounters an exceptional halt.
   *
   * @param frame The message frame
   */
  private void exceptionalHalt(final MessageFrame frame) {
    clearAccumulatedStateBesidesGasAndOutput(frame);
    frame.clearGasRemaining();
    frame.clearOutputData();
    frame.setState(MessageFrame.State.COMPLETED_FAILED);
  }

  /**
   * Gets called when the message frame requests a revert.
   *
   * @param frame The message frame
   */
  protected void revert(final MessageFrame frame) {
    clearAccumulatedStateBesidesGasAndOutput(frame);
    frame.setState(MessageFrame.State.COMPLETED_FAILED);
  }

  /**
   * Gets called when the message frame completes successfully.
   *
   * @param frame The message frame
   */
  private void completedSuccess(final MessageFrame frame) {
    frame.getWorldUpdater().commit();
    frame.getMessageFrameStack().removeFirst();
    frame.notifyCompletion();
  }

  /**
   * Gets called when the message frame execution fails.
   *
   * @param frame The message frame
   */
  private void completedFailed(final MessageFrame frame) {
    frame.getMessageFrameStack().removeFirst();
    frame.notifyCompletion();
  }

  /**
   * Executes the message frame code until it halts.
   *
   * @param frame The message frame
   * @param operationTracer The tracer recording execution
   */
  private void codeExecute(final MessageFrame frame, final OperationTracer operationTracer) {
    try {
      evm.runToHalt(frame, operationTracer);
    } catch (final ModificationNotAllowedException e) {
      frame.setState(MessageFrame.State.REVERT);
    }
  }

  public void process(final MessageFrame frame, final OperationTracer operationTracer) {
    if (frame.getState() == MessageFrame.State.NOT_STARTED) {
      start(frame, operationTracer);
    }

    if (frame.getState() == MessageFrame.State.CODE_EXECUTING) {
      codeExecute(frame, operationTracer);

      if (frame.getState() == MessageFrame.State.CODE_SUSPENDED) {
        return;
      }

      if (frame.getState() == MessageFrame.State.CODE_SUCCESS) {
        codeSuccess(frame, operationTracer);
      }
    }

    if (frame.getState() == MessageFrame.State.EXCEPTIONAL_HALT) {
      exceptionalHalt(frame);
    }

    if (frame.getState() == MessageFrame.State.REVERT) {
      revert(frame);
    }

    if (frame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      completedSuccess(frame);
    }

    if (frame.getState() == MessageFrame.State.COMPLETED_FAILED) {
      completedFailed(frame);
    }
  }

  public Code getCodeFromEVM(final Hash codeHash, final Bytes codeBytes) {
    return evm.getCode(codeHash, codeBytes);
  }
}
