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
package tech.pegasys.pantheon.ethereum.eth.transactions;

import static java.time.Instant.now;

import tech.pegasys.pantheon.ethereum.eth.manager.EthMessage;
import tech.pegasys.pantheon.ethereum.eth.manager.EthMessages.MessageCallback;
import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.messages.TransactionsMessage;

import java.time.Duration;
import java.time.Instant;

class TransactionsMessageHandler implements MessageCallback {

  private final TransactionsMessageProcessor transactionsMessageProcessor;
  private final EthScheduler scheduler;
  private final Duration txMsgKeepAlive;

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
    final TransactionsMessage transactionsMessage = TransactionsMessage.readFrom(message.getData());
    final Instant startedAt = now();
    scheduler.scheduleTxWorkerTask(
        () ->
            transactionsMessageProcessor.processTransactionsMessage(
                message.getPeer(), transactionsMessage, startedAt, txMsgKeepAlive));
  }
}
