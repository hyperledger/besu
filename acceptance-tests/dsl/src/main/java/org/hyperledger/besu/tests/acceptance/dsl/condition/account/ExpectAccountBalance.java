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
package org.hyperledger.besu.tests.acceptance.dsl.condition.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.utils.Convert.toWei;

import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthTransactions;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.web3j.utils.Convert.Unit;

public class ExpectAccountBalance implements Condition {

  private final EthTransactions eth;
  private final Account account;
  private final BigInteger expectedBalance;

  public ExpectAccountBalance(
      final EthTransactions eth,
      final Account account,
      final BigDecimal expectedBalance,
      final Unit balanceUnit) {
    this.account = account;
    this.eth = eth;
    this.expectedBalance = toWei(expectedBalance, balanceUnit).toBigIntegerExact();
  }

  @Override
  public void verify(final Node node) {
    WaitUtils.waitFor(
        () -> assertThat(node.execute(eth.getBalance((account)))).isEqualTo(expectedBalance));
  }
}
