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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy.condition;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.transaction.PrivacyTransactions;

import java.util.List;

import org.awaitility.Awaitility;
import org.web3j.protocol.pantheon.response.privacy.PrivacyGroup;
import org.web3j.utils.Base64String;

public class ExpectValidPrivacyGroupCreated implements PrivateCondition {

  private final PrivacyTransactions transactions;
  private final PrivacyGroup expected;

  public ExpectValidPrivacyGroupCreated(
      final PrivacyTransactions transactions, final PrivacyGroup expected) {
    this.transactions = transactions;
    this.expected = expected;
  }

  @Override
  public void verify(final PrivacyNode node) {
    Awaitility.await()
        .untilAsserted(
            () -> {
              final List<PrivacyGroup> groups =
                  node.execute(
                      transactions.findPrivacyGroup(
                          Base64String.unwrapList(expected.getMembers())));
              assertThat(groups).contains(expected);
            });
  }
}
