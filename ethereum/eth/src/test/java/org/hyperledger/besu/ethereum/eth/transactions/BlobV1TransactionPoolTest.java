/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.testutil.TestClock;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

public class BlobV1TransactionPoolTest extends AbstractTransactionPoolTestBase {

  @Override
  protected PendingTransactions createPendingTransactions(
      final TransactionPoolConfiguration poolConfig,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {

    return new BaseFeePendingTransactionsSorter(
        poolConfig,
        TestClock.system(ZoneId.systemDefault()),
        metricsSystem,
        protocolContext.getBlockchain()::getChainHeadHeader);
  }

  @Override
  protected Transaction createTransaction(final int transactionNumber, final Wei maxPrice) {
    return createTransactionBaseFeeMarket(transactionNumber, maxPrice);
  }

  @Override
  protected TransactionTestFixture createBaseTransaction(final int transactionNumber) {
    return createBaseTransactionBaseFeeMarket(transactionNumber);
  }

  @Override
  protected ExecutionContextTestFixture createExecutionContextTestFixture() {
    return createExecutionContextTestFixtureBaseFeeMarket();
  }

  @Override
  protected FeeMarket getFeeMarket() {
    return FeeMarket.london(0L, Optional.of(BASE_FEE_FLOOR));
  }

  @Override
  protected Block appendBlock(
      final Difficulty difficulty,
      final BlockHeader parentBlock,
      final Transaction... transactionsToAdd) {
    return appendBlockBaseFeeMarket(difficulty, parentBlock, transactionsToAdd);
  }

  @Test
  public void shouldReturnBlobWhenTransactionAddedToPool() {
    givenTransactionIsValid(transactionWithBlobs);

    addAndAssertRemoteTransactionsValid(transactionWithBlobs);

    assertTransactionPending(transactionWithBlobs);
    // assert that the blobs are returned from the tx pool
    final List<BlobsWithCommitments.BlobQuad> expectedBlobQuads =
        transactionWithBlobs.getBlobsWithCommitments().get().getBlobQuads();

    expectedBlobQuads.forEach(
        bq -> assertThat(transactionPool.getBlobQuad(bq.versionedHash())).isEqualTo(bq));
  }

  @Test
  public void shouldNotReturnBlobsWhenAllTxsContainingBlobsHaveBeenReplaced() {
    givenTransactionIsValid(transactionWithBlobs);
    givenTransactionIsValid(transactionWithBlobsReplacement);
    givenTransactionIsValid(transactionWithSameBlobs); // contains same blobs as transactionBlob
    givenTransactionIsValid(transactionWithSameBlobsReplacement);

    addAndAssertRemoteTransactionsValid(transactionWithBlobs);
    assertTransactionPending(transactionWithBlobs);

    final List<BlobsWithCommitments.BlobQuad> expectedBlobQuads =
        transactionWithBlobs.getBlobsWithCommitments().get().getBlobQuads();

    // assert that the blobs are returned from the tx pool
    expectedBlobQuads.forEach(
        bq -> assertThat(transactionPool.getBlobQuad(bq.versionedHash())).isEqualTo(bq));

    // add different transaction that contains the same blobs
    addAndAssertRemoteTransactionsValid(transactionWithSameBlobs);

    assertTransactionPending(transactionWithBlobs);
    assertTransactionPending(transactionWithSameBlobs);
    // assert that the blobs are still returned from the tx pool
    expectedBlobQuads.forEach(
        bq -> assertThat(transactionPool.getBlobQuad(bq.versionedHash())).isEqualTo(bq));

    // replace the second blob transaction with tx with different blobs
    addAndAssertRemoteTransactionsValid(transactionWithSameBlobsReplacement);
    assertTransactionPending(transactionWithSameBlobsReplacement);
    assertTransactionNotPending(transactionWithSameBlobs);

    // assert that the blob is still returned from the tx pool
    expectedBlobQuads.forEach(
        bq -> assertThat(transactionPool.getBlobQuad(bq.versionedHash())).isEqualTo(bq));

    // replace the first blob transaction with tx with different blobs
    addAndAssertRemoteTransactionsValid(transactionWithBlobsReplacement);
    assertTransactionPending(transactionWithBlobsReplacement);
    assertTransactionNotPending(transactionWithBlobs);

    // All txs containing the expected blobs have been replaced,
    // so the blobs should no longer be returned from the tx pool
    expectedBlobQuads.forEach(
        bq -> assertThat(transactionPool.getBlobQuad(bq.versionedHash())).isNull());
  }
}
