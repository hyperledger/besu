/*
 * Copyright contributors to Hyperledger Besu
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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;

public class TransactionPoolEvictionService {
  private final Vertx vertx;
  private final TransactionPool transactionPool;
  private Optional<Long> timerId = Optional.empty();

  public TransactionPoolEvictionService(final Vertx vertx, final TransactionPool transactionPool) {
    this.vertx = vertx;
    this.transactionPool = transactionPool;
  }

  public void start() {
    assert (!timerId.isPresent());
    timerId =
        Optional.of(
            vertx.setPeriodic(
                TimeUnit.MINUTES.toMillis(1),
                id -> transactionPool.getPendingTransactions().evictOldTransactions()));
  }

  public void stop() {
    if (timerId.isPresent()) {
      vertx.cancelTimer(timerId.get());
      timerId = Optional.empty();
    }
  }
}
