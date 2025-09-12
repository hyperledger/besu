/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.tests.acceptance.plugins;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TransactionSelectorPluginTest extends AcceptanceTestBase {
  private BesuNode node;

  @BeforeEach
  public void setUp() throws Exception {
    node =
        besu.createQbftPluginsNode(
            "node",
            Collections.singletonList("testPlugins"),
            List.of(
                "--plugin-tx-selector1-test-enabled=true",
                "--plugin-tx-selector2-test-enabled=true"),
            "DEBUG");
    cluster.start(node);
  }

  @Test
  public void transactionIsMined() {
    final Account recipient = accounts.createAccount("recipient");

    // selector plugins reject values that are multiple of: 3,5,7,11
    final var transferTx =
        accountTransactions.createTransfer(recipient, Amount.wei(BigInteger.ONE));

    final var txHash = node.execute(transferTx);

    node.verify(eth.expectSuccessfulTransactionReceipt(txHash.toHexString()));
  }

  @ParameterizedTest
  // selector plugins reject amount that are multiple of: 3,5,7,11
  @ValueSource(longs = {3, 5, 7, 11, 3 * 5})
  public void badMultipleOfValueTransactionIsNotSelectedByPlugin(final long amount) {
    final Account recipient = accounts.createAccount("recipient");

    final var badTx =
        accountTransactions.createTransfer(recipient, Amount.wei(BigInteger.valueOf(amount)));

    final var sameSenderGoodTx =
        accountTransactions.createTransfer(recipient, Amount.wei(BigInteger.ONE));

    final var anotherSenderGoodTx =
        accountTransactions.createTransfer(
            accounts.getSecondaryBenefactor(), recipient, Amount.wei(BigInteger.ONE));

    final var badHash = node.execute(badTx);
    final var sameSenderGoodHash = node.execute(sameSenderGoodTx);
    final var anotherSenderGoodHash = node.execute(anotherSenderGoodTx);

    node.verify(eth.expectSuccessfulTransactionReceipt(anotherSenderGoodHash.toHexString()));
    node.verify(eth.expectNoTransactionReceipt(badHash.toHexString()));
    // good tx from the same sender is not mined due to the nonce gap
    node.verify(eth.expectNoTransactionReceipt(sameSenderGoodHash.toHexString()));
  }
}
