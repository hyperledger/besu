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
package org.hyperledger.besu.tests.acceptance.dsl.condition.web3;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.web3.Web3Sha3Transaction;

public class ExpectWeb3Sha3Equals implements Condition {

  private final Web3Sha3Transaction input;
  private final String hash;

  public ExpectWeb3Sha3Equals(final Web3Sha3Transaction input, final String expectedHash) {
    this.hash = expectedHash;
    this.input = input;
  }

  @Override
  public void verify(final Node node) {
    assertThat(node.execute(input)).isEqualTo(hash);
  }
}
