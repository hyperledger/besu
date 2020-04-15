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
package org.hyperledger.besu.ethereum.api.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.generatePrivateTransactionMetadataList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.LogTopic;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.core.PrivateTransactionReceiptTestFixture;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.PrivateWorldStateReader;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivacyQueriesTest {

  private final String PRIVACY_GROUP_ID = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private final int NUM_OF_TX_RECEIPTS = 5;
  private final int NUM_OF_BLOCKS = 3;
  private final long FROM_BLOCK_NUMBER = 0;
  private final long TO_BLOCK_NUMBER = 2;

  @Mock private Blockchain blockchain;

  @Mock private PrivateWorldStateReader privateWorldStateReader;

  private PrivacyQueries privacyQueries;

  @Before
  public void before() {
    privacyQueries = new PrivacyQueries(blockchain, privateWorldStateReader);
  }

  @Test
  public void matchingLogsReturnEmptyListForNonExistingBlockHash() {
    final Hash blockHash = Hash.hash(Bytes32.random());
    final LogsQuery query = new LogsQuery.Builder().build();

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.empty());

    final List<LogWithMetadata> logs =
        privacyQueries.matchingLogs(PRIVACY_GROUP_ID, blockHash, query);

    assertThat(logs).isEmpty();
  }

  @Test
  public void matchingLogsReturnEmptyListForNonExistingTransactions() {
    final Hash blockHash = Hash.hash(Bytes32.random());
    final BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    final LogsQuery query = new LogsQuery.Builder().build();

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(privateWorldStateReader.getPrivateTransactionMetadataList(PRIVACY_GROUP_ID, blockHash))
        .thenReturn(Collections.emptyList());

    final List<LogWithMetadata> logs =
        privacyQueries.matchingLogs(PRIVACY_GROUP_ID, blockHash, query);

    assertThat(logs).isEmpty();
  }

  @Test
  public void matchingLogsReturnsEmptyListWhenQueryDoesNotMatch() {
    final Hash blockHash = Hash.hash(Bytes32.random());
    final BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    final LogTopic nonMatchingTopic = LogTopic.of(Bytes32.random());
    final LogsQuery query =
        new LogsQuery.Builder().topics(List.of(List.of(nonMatchingTopic))).build();

    final List<PrivateTransactionMetadata> transactionMetadataList =
        generatePrivateTransactionMetadataList(NUM_OF_TX_RECEIPTS);

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.blockIsOnCanonicalChain(blockHash)).thenReturn(true);
    when(privateWorldStateReader.getPrivateTransactionMetadataList(PRIVACY_GROUP_ID, blockHash))
        .thenReturn(transactionMetadataList);

    mockReceiptsWithLogsAndTopics(blockHash, transactionMetadataList, null);

    final List<LogWithMetadata> logs =
        privacyQueries.matchingLogs(PRIVACY_GROUP_ID, blockHash, query);

    assertThat(logs).isEmpty();
  }

  @Test
  public void matchingLogsReturnsAllLogsThatMatchQuery() {
    final Hash blockHash = Hash.hash(Bytes32.random());
    final BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    final LogTopic matchingTopic = LogTopic.of(Bytes32.random());
    final LogsQuery query = new LogsQuery.Builder().topics(List.of(List.of(matchingTopic))).build();

    final List<PrivateTransactionMetadata> transactionMetadataList =
        generatePrivateTransactionMetadataList(NUM_OF_TX_RECEIPTS);

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.blockIsOnCanonicalChain(blockHash)).thenReturn(true);
    when(privateWorldStateReader.getPrivateTransactionMetadataList(PRIVACY_GROUP_ID, blockHash))
        .thenReturn(transactionMetadataList);

    mockReceiptsWithLogsAndTopics(blockHash, transactionMetadataList, matchingTopic);

    final List<LogWithMetadata> logs =
        privacyQueries.matchingLogs(PRIVACY_GROUP_ID, blockHash, query);

    assertThat(logs).hasSize(transactionMetadataList.size());
  }

  @Test
  public void matchingLogsByBlockRangeReturnsEmptyWhenBlocksDontExist() {
    final LogsQuery query = new LogsQuery.Builder().build();

    LongStream.rangeClosed(FROM_BLOCK_NUMBER, TO_BLOCK_NUMBER)
        .forEach(i -> when(blockchain.getBlockHashByNumber(i)).thenReturn(Optional.empty()));

    final List<LogWithMetadata> logs =
        privacyQueries.matchingLogs(PRIVACY_GROUP_ID, FROM_BLOCK_NUMBER, TO_BLOCK_NUMBER, query);

    // only called once because we "break the chain" using takeWhile
    verify(blockchain).getBlockHashByNumber(anyLong());

    assertThat(logs).isEmpty();
  }

  @Test
  public void matchingLogsByBlockRangeReturnsAllLogsThatMatchQuery() {
    final LogTopic matchingTopic = LogTopic.of(Bytes32.random());
    final LogsQuery query = new LogsQuery.Builder().topics(List.of(List.of(matchingTopic))).build();

    LongStream.rangeClosed(FROM_BLOCK_NUMBER, TO_BLOCK_NUMBER)
        .forEach(
            i -> {
              final Hash blockHash = Hash.hash(Bytes32.random());
              final BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();

              final List<PrivateTransactionMetadata> transactionMetadataList =
                  generatePrivateTransactionMetadataList(NUM_OF_TX_RECEIPTS);

              when(blockchain.getBlockHashByNumber(i)).thenReturn(Optional.of(blockHash));
              when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
              when(blockchain.blockIsOnCanonicalChain(blockHash)).thenReturn(true);
              when(privateWorldStateReader.getPrivateTransactionMetadataList(
                      PRIVACY_GROUP_ID, blockHash))
                  .thenReturn(transactionMetadataList);

              mockReceiptsWithLogsAndTopics(blockHash, transactionMetadataList, matchingTopic);
            });

    final List<LogWithMetadata> logs =
        privacyQueries.matchingLogs(PRIVACY_GROUP_ID, FROM_BLOCK_NUMBER, TO_BLOCK_NUMBER, query);

    verify(blockchain, times(NUM_OF_BLOCKS)).getBlockHashByNumber(anyLong());
    verify(privateWorldStateReader, times(NUM_OF_BLOCKS * NUM_OF_TX_RECEIPTS))
        .getPrivateTransactionReceipt(any(), any());

    assertThat(logs).hasSize(NUM_OF_BLOCKS * NUM_OF_TX_RECEIPTS);
  }

  private void mockReceiptsWithLogsAndTopics(
      final Hash blockHash,
      final List<PrivateTransactionMetadata> transactionMetadataList,
      final LogTopic topic) {

    transactionMetadataList.forEach(
        metadata -> {
          final Log log =
              new Log(
                  Address.ZERO,
                  Bytes.EMPTY,
                  topic != null ? List.of(topic) : Collections.emptyList());
          final PrivateTransactionReceipt receipt =
              new PrivateTransactionReceiptTestFixture().logs(List.of(log)).create();

          when(privateWorldStateReader.getPrivateTransactionReceipt(
                  blockHash, metadata.getPrivacyMarkerTransactionHash()))
              .thenReturn(Optional.of(receipt));
        });
  }
}
