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
package org.hyperledger.besu.tests.acceptance.dsl.condition.priv;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivDeletePrivacyGroupTransaction;

public class PrivDeletePrivacyGroupSuccess implements Condition {

  private final PrivDeletePrivacyGroupTransaction transaction;
  private final String groupId;

  public PrivDeletePrivacyGroupSuccess(
      final PrivDeletePrivacyGroupTransaction transaction, final String groupId) {
    this.transaction = transaction;
    this.groupId = groupId;
  }

  @Override
  public void verify(final Node node) {
    final String result = node.execute(transaction);
    assertThat(result).isEqualTo(groupId);
  }
}
