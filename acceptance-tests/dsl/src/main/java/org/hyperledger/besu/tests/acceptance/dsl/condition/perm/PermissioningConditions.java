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
package org.hyperledger.besu.tests.acceptance.dsl.condition.perm;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.ethereum.permissioning.AllowlistPersistor.ALLOWLIST_TYPE;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.PermissioningTransactions;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class PermissioningConditions {

  public PermissioningConditions(final PermissioningTransactions transactions) {
    this.transactions = transactions;
  }

  private final PermissioningTransactions transactions;

  public Condition addAccountsToAllowlist(final String... accounts) {
    return new AddAccountsToWhitelistSuccessfully(transactions.addAccountsToAllowlist(accounts));
  }

  public Condition removeAccountsFromAllowlist(final String... accounts) {
    return new RemoveAccountsFromWhitelistSuccessfully(
        transactions.removeAccountsFromAllowlist(accounts));
  }

  public Condition expectAccountsAllowlist(final String... expectedAccounts) {
    return new GetExpectedAccountsWhitelist(
        transactions.getAccountsWhiteList(), asList(expectedAccounts));
  }

  public Condition addNodesToAllowlist(final String... nodes) {
    return addNodesToAllowlist(Stream.of(nodes).map(URI::create).collect(toList()));
  }

  public Condition addNodesToAllowlist(final Node... nodes) {
    final List<URI> enodeList = toEnodeUris(nodes);
    return addNodesToAllowlist(enodeList);
  }

  private Condition addNodesToAllowlist(final List<URI> enodeList) {
    return new AddNodeSuccess(transactions.addNodesToAllowlist(enodeList));
  }

  public Condition removeNodesFromAllowlist(final String... nodes) {
    return removeNodesFromAllowlist(Stream.of(nodes).map(URI::create).collect(toList()));
  }

  public Condition removeNodesFromAllowlist(final Node... nodes) {
    final List<URI> enodeList = toEnodeUris(nodes);
    return removeNodesFromAllowlist(enodeList);
  }

  private Condition removeNodesFromAllowlist(final List<URI> enodeList) {
    return new RemoveNodeSuccess(transactions.removeNodesFromAllowlist(enodeList));
  }

  public Condition getNodesAllowlist(final int expectedNodeNum) {
    return new GetNodesWhitelistPopulated(transactions.getNodesWhiteList(), expectedNodeNum);
  }

  public Condition expectPermissioningAllowlistFileKeyValue(
      final ALLOWLIST_TYPE allowlistType, final Path configFilePath, final String... val) {
    return new AllowListContainsKeyAndValue(allowlistType, asList(val), configFilePath);
  }

  private List<URI> toEnodeUris(final Node[] nodes) {
    return Stream.of(nodes)
        .map(node -> (RunnableNode) node)
        .map(RunnableNode::enodeUrl)
        .collect(toList());
  }
}
