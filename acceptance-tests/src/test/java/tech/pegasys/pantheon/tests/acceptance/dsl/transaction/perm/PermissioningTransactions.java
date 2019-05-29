/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm;

import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.login.LoginSuccessfulTransaction;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

public class PermissioningTransactions {

  public PermAddAccountsToWhitelistTransaction addAccountsToWhitelist(final String... accounts) {
    return new PermAddAccountsToWhitelistTransaction(Arrays.asList(accounts));
  }

  public PermRemoveAccountsFromWhitelistTransaction removeAccountsFromWhitelist(
      final String... accounts) {
    return new PermRemoveAccountsFromWhitelistTransaction(Arrays.asList(accounts));
  }

  public PermGetAccountsWhitelistTransaction getAccountsWhiteList() {
    return new PermGetAccountsWhitelistTransaction();
  }

  public PermAddNodeTransaction addNodesToWhitelist(final List<URI> enodeList) {
    return new PermAddNodeTransaction(enodeList);
  }

  public PermRemoveNodeTransaction removeNodesFromWhitelist(final List<URI> enodeList) {
    return new PermRemoveNodeTransaction(enodeList);
  }

  public PermGetNodesWhitelistTransaction getNodesWhiteList() {
    return new PermGetNodesWhitelistTransaction();
  }

  public LoginSuccessfulTransaction createSuccessfulLogin(
      final String username, final String password) {
    return new LoginSuccessfulTransaction(username, password);
  }
}
