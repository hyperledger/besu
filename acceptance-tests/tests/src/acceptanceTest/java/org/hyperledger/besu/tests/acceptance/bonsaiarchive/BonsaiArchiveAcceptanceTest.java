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
package org.hyperledger.besu.tests.acceptance.bonsaiarchive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.utils.Convert.toWei;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;

public class BonsaiArchiveAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldMineBlocksWithBonsaiArchive() throws Exception {
    final BesuNode validator1 =
        besu.createQbftNode("validator1", false, DataStorageFormat.X_BONSAI_ARCHIVE);
    cluster.start(validator1);
    cluster.verify(blockchain.reachesHeight(validator1, 1));

    final Account sender = accounts.getPrimaryBenefactor();
    final Account receiver1 = accounts.createAccount("receiver1");
    final Account receiver2 = accounts.createAccount("receiver2");

    // Send transactions across multiple blocks to test flat DB context handling
    validator1.execute(accountTransactions.createTransfer(sender, receiver1, 1));
    cluster.verify(receiver1.balanceEquals(1)); // receives 1 ether

    validator1.execute(accountTransactions.createTransfer(sender, receiver2, 2));
    cluster.verify(receiver1.balanceEquals(1)); // still has 1 ether
    cluster.verify(receiver2.balanceEquals(2)); // receives 2 ether

    validator1.execute(accountTransactions.createTransfer(sender, receiver1, 3));
    cluster.verify(receiver1.balanceEquals(4)); // receives another 3 ether
    cluster.verify(receiver2.balanceEquals(2)); // still has 2 ether

    validator1.execute(accountTransactions.createTransfer(sender, receiver2, 4));
    cluster.verify(receiver1.balanceEquals(4)); // still have 4 ether
    cluster.verify(receiver2.balanceEquals(6)); // receives another 4 ether
  }

  @Test
  public void shouldQueryHistoricalStateWithBonsaiArchive() throws Exception {
    final BesuNode validator1 =
        besu.createQbftNode("validator1", false, DataStorageFormat.X_BONSAI_ARCHIVE);
    cluster.start(validator1);
    cluster.verify(blockchain.reachesHeight(validator1, 1));

    final Account sender = accounts.getPrimaryBenefactor();
    final Account receiver = accounts.createAccount("receiver");

    final Hash tx1Hash =
        validator1.execute(accountTransactions.createTransfer(sender, receiver, 1));
    final TransactionReceipt tx1Receipt = waitForTransactionReceipt(validator1, tx1Hash);
    final BigInteger tx1Block = tx1Receipt.getBlockNumber();

    final Hash tx2Hash =
        validator1.execute(accountTransactions.createTransfer(sender, receiver, 2));
    final TransactionReceipt tx2Receipt = waitForTransactionReceipt(validator1, tx2Hash);
    final BigInteger tx2Block = tx2Receipt.getBlockNumber();

    final Hash tx3Hash =
        validator1.execute(accountTransactions.createTransfer(sender, receiver, 3));
    final TransactionReceipt tx3Receipt = waitForTransactionReceipt(validator1, tx3Hash);
    final BigInteger tx3Block = tx3Receipt.getBlockNumber();

    final Hash tx4Hash =
        validator1.execute(accountTransactions.createTransfer(sender, receiver, 4));
    final TransactionReceipt tx4Receipt = waitForTransactionReceipt(validator1, tx4Hash);
    final BigInteger tx4Block = tx4Receipt.getBlockNumber();

    // Verify archive nodes can query historical state at the actual blocks where txs were included
    final BigInteger oneEther = toWei(BigDecimal.ONE, Convert.Unit.ETHER).toBigIntegerExact();

    // After tx1 (1 ether transferred), balance should be 1 ether
    final BigInteger balanceAtTx1Block =
        validator1.execute(ethTransactions.getBalanceAtBlock(receiver, tx1Block));
    assertThat(balanceAtTx1Block).isEqualTo(oneEther);

    // After tx2 (2 ether transferred), balance should be 1+2=3 ether
    final BigInteger balanceAtTx2Block =
        validator1.execute(ethTransactions.getBalanceAtBlock(receiver, tx2Block));
    assertThat(balanceAtTx2Block).isEqualTo(oneEther.multiply(BigInteger.valueOf(3)));

    // After tx3 (3 ether transferred), balance should be 1+2+3=6 ether
    final BigInteger balanceAtTx3Block =
        validator1.execute(ethTransactions.getBalanceAtBlock(receiver, tx3Block));
    assertThat(balanceAtTx3Block).isEqualTo(oneEther.multiply(BigInteger.valueOf(6)));

    // After tx4 (4 ether transferred), balance should be 1+2+3+4=10 ether
    final BigInteger balanceAtTx4Block =
        validator1.execute(ethTransactions.getBalanceAtBlock(receiver, tx4Block));
    assertThat(balanceAtTx4Block).isEqualTo(oneEther.multiply(BigInteger.valueOf(10)));

    // Verify current balance matches final historical balance
    final BigInteger currentBalance = validator1.execute(ethTransactions.getBalance(receiver));
    assertThat(currentBalance).isEqualTo(oneEther.multiply(BigInteger.valueOf(10)));
  }

  private TransactionReceipt waitForTransactionReceipt(
      final BesuNode node, final Hash transactionHash) {
    final AtomicReference<Optional<TransactionReceipt>> receiptHolder =
        new AtomicReference<>(Optional.empty());
    WaitUtils.waitFor(
        () -> {
          receiptHolder.set(
              node.execute(ethTransactions.getTransactionReceipt(transactionHash.toString())));
          assertThat(receiptHolder.get()).isPresent();
        });
    return receiptHolder.get().orElseThrow();
  }
}
