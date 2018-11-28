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
package tech.pegasys.pantheon.tests.acceptance.dsl.account;

import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eth.EthTransactions;

public class Accounts {

  public static final String GENESIS_ACCOUNT_ONE_PRIVATE_KEY =
      "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";

  private final EthTransactions eth;
  private final Account richBenefactorOne;
  private final Account richBenefactorTwo;

  public Accounts(final EthTransactions eth) {
    this.eth = eth;
    richBenefactorOne =
        Account.fromPrivateKey(eth, "Rich Benefactor One", GENESIS_ACCOUNT_ONE_PRIVATE_KEY);
    richBenefactorTwo =
        Account.fromPrivateKey(
            eth,
            "Rich Benefactor Two",
            "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3");
  }

  public Account getSecondaryBenefactor() {
    return richBenefactorTwo;
  }

  public Account getPrimaryBenefactor() {
    return richBenefactorOne;
  }

  public Account createAccount(final String accountName) {
    return Account.create(eth, accountName);
  }
}
