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

package org.hyperledger.besu.ethereum.privacy;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.privacy.PrivacyMarkerTransactionPool.PrivacyMarkerTransactionTracker;

import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class PrivacyMarkerTransactionPoolTest {
  private static final KeyPair KEY_PAIR1 =
      SignatureAlgorithmFactory.getInstance().generateKeyPair();
  private static final String PRIVACY_GROUP_ID = "DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w=";
  private static final String PRIVACY_GROUP_ID_TWO = "sNDXVbqpoF17T+ajqyn2n+kMhBFCA23zCxdl+UtUbrY=";
  private static final String ADDRESS_ONE = "55";
  private static final String ADDRESS_TWO = "22";
  private static final String ADDRESS_THREE = "33";
  private static final Hash HASH_ONE =
      Hash.fromHexString("0x111155949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c6911111");
  private static final Hash HASH_TWO =
      Hash.fromHexString("0x222255949038a9610f50fb23b5883af3b4ecb3c3bb79000000001542c6922222");
  private static final Hash HASH_THREE =
      Hash.fromHexString("0x222255949038a9610f50fb23b5883af3b4ecb3c3bb79000000001542c6933333");

  private MutableBlockchain blockchain;
  private final ExecutionContextTestFixture executionContext = ExecutionContextTestFixture.create();
  private PrivacyMarkerTransactionPool pmtPool;

  private final Transaction tx1 = createTransaction(1);

  @Before
  public void setUp() {
    blockchain = executionContext.getBlockchain();

    pmtPool = new PrivacyMarkerTransactionPool(blockchain);
    blockchain.observeBlockAdded(pmtPool);
  }

  @Test
  public void matchingTxInPmtPool_determineCorrectPrivateNonces() {
    pmtPool.addPmtTransactionTracker(
        HASH_ONE, ADDRESS_ONE, PRIVACY_GROUP_ID, 5L, 99L, Optional.empty());
    pmtPool.addPmtTransactionTracker(
        HASH_THREE, ADDRESS_ONE, PRIVACY_GROUP_ID_TWO, 0L, 99L, Optional.empty());
    pmtPool.addPmtTransactionTracker(
        HASH_TWO, ADDRESS_TWO, PRIVACY_GROUP_ID, 99L, 99L, Optional.empty());

    assertThat(pmtPool.getActiveCount()).isEqualTo(3L);
    assertThat(pmtPool.getMaxMatchingNonce(ADDRESS_ONE, PRIVACY_GROUP_ID))
        .isEqualTo(Optional.of(5L));
    assertThat(pmtPool.getMaxMatchingNonce(ADDRESS_ONE, PRIVACY_GROUP_ID_TWO))
        .isEqualTo(Optional.of(0L));
    assertThat(pmtPool.getMaxMatchingNonce(ADDRESS_TWO, PRIVACY_GROUP_ID))
        .isEqualTo(Optional.of(99L));
    assertThat(pmtPool.getMaxMatchingNonce(ADDRESS_THREE, PRIVACY_GROUP_ID))
        .isEqualTo(Optional.empty());
    assertThat(pmtPool.getMaxMatchingNonce(ADDRESS_TWO, PRIVACY_GROUP_ID_TWO))
        .isEqualTo(Optional.empty());
  }

  @Test
  public void shouldRemoveTransactionsFromPendingListWhenIncludedInBlockOnchain()
      throws InterruptedException {
    final PrivacyMarkerTransactionTracker tx1Tracker =
        new PrivacyMarkerTransactionTracker(
            tx1.getHash(), ADDRESS_ONE, PRIVACY_GROUP_ID, 66L, 99L, Optional.of(Wei.ZERO));
    pmtPool.addPmtTransactionTracker(tx1Tracker);
    System.out.println(tx1Tracker);
    assertTransactionPending(tx1.getHash(), tx1Tracker);
    assertThat(pmtPool.getActiveCount()).isEqualTo(1L);
    appendBlock(tx1);

    Thread.sleep(20);
    assertTransactionNotPending(tx1);
    assertThat(pmtPool.getActiveCount()).isEqualTo(0L);
  }

  private void assertTransactionPending(final Hash hash, final PrivacyMarkerTransactionTracker t) {
    assertThat(pmtPool.getTransactionByHash(hash, true)).contains(t);
  }

  private void assertTransactionNotPending(final Transaction transaction) {
    Optional<PrivacyMarkerTransactionTracker> tracker =
        pmtPool.getTransactionByHash(transaction.getHash(), true);
    assertThat(tracker).isEmpty();
  }

  private Transaction createTransaction(final int transactionNumber) {
    return new TransactionTestFixture()
        .nonce(transactionNumber)
        .gasLimit(0)
        .createTransaction(KEY_PAIR1);
  }

  private void appendBlock(final Transaction... transactionsToAdd) {
    appendBlock(Difficulty.ONE, getHeaderForCurrentChainHead(), transactionsToAdd);
  }

  private BlockHeader getHeaderForCurrentChainHead() {
    return blockchain.getBlockHeader(blockchain.getChainHeadHash()).get();
  }

  private Block appendBlock(
      final Difficulty difficulty,
      final BlockHeader parentBlock,
      final Transaction... transactionsToAdd) {
    final List<Transaction> transactionList = asList(transactionsToAdd);
    final Block block =
        new Block(
            new BlockHeaderTestFixture()
                .difficulty(difficulty)
                .parentHash(parentBlock.getHash())
                .number(parentBlock.getNumber() + 1)
                .buildHeader(),
            new BlockBody(transactionList, emptyList()));
    final List<TransactionReceipt> transactionReceipts =
        transactionList.stream()
            .map(transaction -> new TransactionReceipt(1, 1, emptyList(), Optional.empty()))
            .collect(toList());
    blockchain.appendBlock(block, transactionReceipts);
    return block;
  }
}
