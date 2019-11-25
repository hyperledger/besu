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
package org.hyperledger.besu.tests.acceptance.dsl.condition.net;

import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.net.NetTransactions;

import java.math.BigInteger;

public class NetConditions {

  private final NetTransactions transactions;

  public NetConditions(final NetTransactions transactions) {
    this.transactions = transactions;
  }

  public Condition netServicesAllActive() {
    return new ExpectNetServicesReturnsAllServicesAsActive(transactions.netServices());
  }

  public Condition netServicesOnlyJsonRpcEnabled() {
    return new ExpectNetServicesReturnsOnlyJsonRpcActive(transactions.netServices());
  }

  public Condition netServicesUnauthorized() {
    return new ExpectUnauthorized(transactions.netServices());
  }

  public Condition netVersion() {
    return new ExpectNetVersionIsNotBlank(transactions.netVersion());
  }

  public Condition awaitPeerCount(final int awaitPeerCount) {
    return new AwaitNetPeerCount(transactions.peerCount(), BigInteger.valueOf(awaitPeerCount));
  }

  public Condition netVersionExceptional(final String expectedMessage) {
    return new ExpectNetVersionConnectionException(transactions.netVersion(), expectedMessage);
  }

  public Condition netVersionUnauthorized() {
    return new ExpectUnauthorized(transactions.netVersion());
  }

  public Condition awaitPeerCountExceptional() {
    return new AwaitNetPeerCountException(transactions.peerCount());
  }
}
