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
package org.hyperledger.besu.tests.acceptance.dsl.condition.clique;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.tests.acceptance.dsl.condition.clique.ExpectNonceVote.CLIQUE_NONCE_VOTE.AUTH;

import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthTransactions;

public class ExpectNonceVote implements Condition {
  private static final String NONCE_AUTH = "0xffffffffffffffff";
  private static final String NONCE_DROP = "0x0000000000000000";
  private final EthTransactions eth;
  private final String expectedNonce;

  public enum CLIQUE_NONCE_VOTE {
    AUTH,
    DROP
  }

  public ExpectNonceVote(final EthTransactions eth, final CLIQUE_NONCE_VOTE vote) {
    this.eth = eth;
    this.expectedNonce = vote == AUTH ? NONCE_AUTH : NONCE_DROP;
  }

  @Override
  public void verify(final Node node) {
    WaitUtils.waitFor(
        () -> assertThat(node.execute(eth.block()).getNonceRaw()).isEqualTo(expectedNonce));
  }
}
