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
package tech.pegasys.pantheon.tests.acceptance.dsl;

import tech.pegasys.pantheon.tests.acceptance.dsl.account.Accounts;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Cluster;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transactions;

import org.junit.After;

public class AcceptanceTestBase {

  protected final Accounts accounts;
  protected final Cluster cluster;
  protected final Transactions transactions;
  protected final JsonRpc jsonRpc;

  protected AcceptanceTestBase() {
    accounts = new Accounts();
    cluster = new Cluster();
    transactions = new Transactions(accounts);
    jsonRpc = new JsonRpc(cluster);
  }

  @After
  public void tearDownAcceptanceTestBase() {
    cluster.close();
  }
}
