/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.vm.ehalt.ExceptionalHaltException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

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
 * <td>{@link AbstractMessageProcessor#start(MessageFrame)}</td>
 * </tr>
 * <tr>
 * <td>{@link MessageFrame.State#CODE_EXECUTING}</td>
 * <td>{@link AbstractMessageProcessor#codeExecute(MessageFrame, OperationTracer)}</td>
 * </tr>
 * <tr>
 * <td>{@link MessageFrame.State#CODE_SUCCESS}</td>
 * <td>{@link AbstractMessageProcessor#codeSuccess(MessageFrame)}</td>
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
  private final Collection<Address> forceDeleteAccountsWhenEmpty;
  private final EVM evm;

  protected AbstractMessageProcessor(
      final EVM evm, final Collection<Address> forceDeleteAccountsWhenEmpty) {
    this.evm = evm;
    this.forceDeleteAccountsWhenEmpty = forceDeleteAccountsWhenEmpty;
  }

  protected abstract void start(MessageFrame frame);

  /**
   * Gets called when the message frame code executes successfully.
   *
   * @param frame The message frame
   */
  protected abstract void codeSuccess(MessageFrame frame);

  private void clearAccumulatedStateBesidesGasAndOutput(final MessageFrame frame) {
    final Collection<Address> addressesToForceCommit =
        frame.getWorldState().getTouchedAccounts().stream()
            .filter(a -> forceDeleteAccountsWhenEmpty.contains(a.getAddress()) && a.isEmpty())
            .map(Account::getAddress)
            .collect(Collectors.toCollection(ArrayList::new));

    // Clear any pending changes.
    frame.getWorldState().revert();

    // Force delete any requested accounts and commit the changes.
    addressesToForceCommit.forEach(h -> frame.getWorldState().deleteAccount(h));
    frame.getWorldState().commit();

    frame.clearLogs();
    frame.clearSelfDestructs();
    frame.clearGasRefund();
  }

  /**
   * Gets called when the message frame encounters an exceptional halt.
   *
   * @param frame The message frame
   */
  protected void exceptionalHalt(final MessageFrame frame) {
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
  protected void completedSuccess(final MessageFrame frame) {
    frame.getWorldState().commit();
    frame.getMessageFrameStack().removeFirst();
    frame.notifyCompletion();
  }

  /**
   * Gets called when the message frame execution fails.
   *
   * @param frame The message frame
   */
  protected void completedFailed(final MessageFrame frame) {
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
    } catch (final ExceptionalHaltException e) {
      frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
    }
  }

  public void process(final MessageFrame frame, final OperationTracer operationTracer) {
    if (frame.getState() == MessageFrame.State.NOT_STARTED) {
      start(frame);
    }

    if (frame.getState() == MessageFrame.State.CODE_EXECUTING) {
      codeExecute(frame, operationTracer);

      if (frame.getState() == MessageFrame.State.CODE_SUSPENDED) {
        return;
      }

      if (frame.getState() == MessageFrame.State.CODE_SUCCESS) {
        codeSuccess(frame);
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
}
