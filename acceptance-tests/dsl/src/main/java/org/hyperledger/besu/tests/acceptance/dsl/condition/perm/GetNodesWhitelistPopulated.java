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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.PermGetNodesWhitelistTransaction;

import java.util.List;

public class GetNodesWhitelistPopulated implements Condition {

  private final PermGetNodesWhitelistTransaction transaction;
  private final int expectedNodeNum;

  public GetNodesWhitelistPopulated(
      final PermGetNodesWhitelistTransaction transaction, final int expectedNodeNum) {
    this.transaction = transaction;
    this.expectedNodeNum = expectedNodeNum;
  }

  @Override
  public void verify(final Node node) {
    final List<String> response = node.execute(transaction);
    assertThat(response).isInstanceOf(List.class);
    assertThat(response).size().isEqualTo(expectedNodeNum);
  }
}
