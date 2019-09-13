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
package tech.pegasys.pantheon.tests.acceptance.dsl.condition.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.utils.Convert.toWei;

import tech.pegasys.pantheon.tests.acceptance.dsl.account.Account;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eth.EthTransactions;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.web3j.utils.Convert.Unit;

public class ExpectAccountBalanceNotChanging implements Condition {

  private final EthTransactions eth;
  private final Account account;
  private final String startBalance;
  private final Unit balanceUnit;

  public ExpectAccountBalanceNotChanging(
      final EthTransactions eth,
      final Account account,
      final String startBalance,
      final Unit balanceUnit) {
    this.startBalance = startBalance;
    this.balanceUnit = balanceUnit;
    this.account = account;
    this.eth = eth;
  }

  @Override
  public void verify(final Node node) {
    Awaitility.await()
        .ignoreExceptions()
        .pollDelay(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(node.execute(eth.getBalance((account))))
                    .isEqualTo(toWei(startBalance, balanceUnit).toBigIntegerExact()));
  }
}
