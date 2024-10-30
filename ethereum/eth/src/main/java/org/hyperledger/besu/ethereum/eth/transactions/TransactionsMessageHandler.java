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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.time.Instant.now;

import org.hyperledger.besu.ethereum.eth.manager.EthMessage;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.messages.TransactionsMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

class TransactionsMessageHandler implements EthMessages.MessageCallback {

  private final TransactionsMessageProcessor transactionsMessageProcessor;
  private final EthScheduler scheduler;
  private final Duration txMsgKeepAlive;
  private final AtomicBoolean isEnabled = new AtomicBoolean(false);

  public TransactionsMessageHandler(
      final EthScheduler scheduler,
      final TransactionsMessageProcessor transactionsMessageProcessor,
      final int txMsgKeepAliveSeconds) {
    this.scheduler = scheduler;
    this.transactionsMessageProcessor = transactionsMessageProcessor;
    this.txMsgKeepAlive = Duration.ofSeconds(txMsgKeepAliveSeconds);
  }

  @Override
  public void exec(final EthMessage message) {
    if (isEnabled.get()) {
      final TransactionsMessage transactionsMessage =
          TransactionsMessage.readFrom(message.getData());
      final Instant startedAt = now();
      scheduler.scheduleTxWorkerTask(
          () ->
              transactionsMessageProcessor.processTransactionsMessage(
                  message.getPeer(), transactionsMessage, startedAt, txMsgKeepAlive));
    }
  }

  public void setDisabled() {
    isEnabled.set(false);
  }

  public void setEnabled() {
    isEnabled.set(true);
  }

  public boolean isEnabled() {
    return isEnabled.get();
  }
}
