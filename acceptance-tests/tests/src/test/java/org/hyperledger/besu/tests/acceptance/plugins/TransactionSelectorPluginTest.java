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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
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

  @Test
  public void unhandledExceptionInPluginSelectorIsManaged() {
    final Account recipient = accounts.createAccount("recipient");

    // selectors are badly coded, and they do not manage properly when the value
    // of the transfer does not fit in a long, throwing an exception, that is
    // managed by the block creation, that will ignore that tx and continue.
    final var overflowValue = Amount.wei(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN));
    final var baseGasPrice = Amount.wei(BigInteger.valueOf(10_000_000_000L)); // 10 gwei

    // we send this first tx just to make sure that we send the following 2 txs
    // just after a block has been imported, and so the probability that the 2
    // txs are evaluated during the same block creation is very high
    final var dummyTransferTx =
        accountTransactions.createTransfer(
            accounts.getPrimaryBenefactor(),
            recipient,
            Amount.wei(BigInteger.valueOf(1_000_000_000_000_013L)), // non-divisible by 3,5,7,11
            baseGasPrice);

    // wait for the next block to be a multiple of 3 or 5, since that will make
    // the selectPendingTransactions of the plugins throw an unhandled exception.
    final var nextBlockNumber = new AtomicReference<BigInteger>();
    Awaitility.await()
        .until(
            () -> {
              final var nextBlock = node.execute(ethTransactions.blockNumber()).add(BigInteger.ONE);
              if (nextBlock.mod(BigInteger.valueOf(3)).equals(BigInteger.ZERO)
                  || nextBlock.mod(BigInteger.valueOf(5)).equals(BigInteger.ZERO)) {
                nextBlockNumber.set(nextBlock);
                return true;
              }
              return false;
            });

    final var dummyTransferTxHash = node.execute(dummyTransferTx);

    node.verify(eth.expectSuccessfulTransactionReceipt(dummyTransferTxHash.toHexString()));
    final var dummyTransferReceipt =
        node.execute(ethTransactions.getTransactionReceipt(dummyTransferTxHash.toHexString()));
    assertThat(dummyTransferReceipt.get().getBlockNumber()).isEqualTo(nextBlockNumber.get());

    // next we send 2 tx, the first tx throws an unhandled exception from a plugin validator
    // the second is a good one, and we expected the first to be rejected
    // and the second to be selected

    // this tx pays more for gas so it is evaluated first
    final var throwingTransferTx =
        accountTransactions.createTransfer(
            accounts.getSecondaryBenefactor(),
            recipient,
            overflowValue,
            baseGasPrice.add(Amount.wei(BigInteger.ONE)));

    final var goodTransferTx =
        accountTransactions.createTransfer(
            recipient, accounts.getSecondaryBenefactor(), Amount.wei(BigInteger.ONE), baseGasPrice);

    // we check the block number, because we want to be sure that the 2 txs have been
    // evaluated during the same block creation
    final var preSendBlock = node.execute(ethTransactions.blockNumber());
    final var throwingTxHash = node.execute(throwingTransferTx);
    final var goodTxHash = node.execute(goodTransferTx);

    node.verify(eth.expectSuccessfulTransactionReceipt(goodTxHash.toHexString()));

    final var goodReceipt =
        node.execute(ethTransactions.getTransactionReceipt(goodTxHash.toHexString()));
    assertThat(goodReceipt.get().getBlockNumber()).isEqualTo(preSendBlock.add(BigInteger.ONE));

    node.verify(eth.expectNoTransactionReceipt(throwingTxHash.toHexString()));
  }
}
