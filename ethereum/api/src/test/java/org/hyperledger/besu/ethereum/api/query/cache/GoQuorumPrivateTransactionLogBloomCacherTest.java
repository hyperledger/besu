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
 *
 */

package org.hyperledger.besu.ethereum.api.query.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.query.cache.TransactionLogBloomCacher.BLOOM_BITS_LENGTH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("unused")
@RunWith(MockitoJUnitRunner.class)
public class GoQuorumPrivateTransactionLogBloomCacherTest {

  private static final long NUMBER_3 = 3L;
  private static LogsQuery logsQuery;
  @Rule public TemporaryFolder cacheDir = new TemporaryFolder();

  private Hash testBlockHeaderHash;
  private static LogsBloomFilter testLogsBloomFilter;

  @Mock MutableBlockchain blockchain;
  @Mock EthScheduler scheduler;
  @Mock WorldStateArchive worldStateArchive;

  private TransactionLogBloomCacher transactionLogBloomCacher;
  private BlockchainQueries blockchainQueries;
  private static Address testAddress;
  private static Bytes testMessage;
  private BlockHeader fakeHeader;

  @BeforeClass
  public static void setupClass() {
    testAddress = Address.fromHexString("0x123456");
    testMessage = Bytes.fromHexString("0x9876");
    final Log testLog = new Log(testAddress, testMessage, List.of());
    testLogsBloomFilter = LogsBloomFilter.builder().insertLog(testLog).build();
    logsQuery = new LogsQuery(List.of(testAddress), List.of());
  }

  @SuppressWarnings({"unchecked", "ReturnValueIgnored"})
  @Before
  public void setup() throws IOException {
    fakeHeader = createBlock(NUMBER_3);

    testBlockHeaderHash = fakeHeader.getHash();

    transactionLogBloomCacher =
        new TransactionLogBloomCacher(blockchain, cacheDir.getRoot().toPath(), scheduler);

    blockchainQueries =
        new BlockchainQueries(
            blockchain,
            worldStateArchive,
            Optional.of(cacheDir.getRoot().toPath()),
            Optional.of(scheduler));
  }

  @Test
  public void shouldUpdateCacheWhenBlockAdded() throws IOException {

    final BlockBody fakeBody = new BlockBody(Collections.emptyList(), Collections.emptyList());
    when(blockchain.getBlockHeader(testBlockHeaderHash)).thenReturn(Optional.of(fakeHeader));
    when(blockchain.getBlockHashByNumber(anyLong())).thenReturn(Optional.of(testBlockHeaderHash));
    when(blockchain.getTxReceipts(any())).thenReturn(Optional.of(Collections.emptyList()));
    when(blockchain.getBlockBody(any())).thenReturn(Optional.of(fakeBody));

    final File logBloom = cacheDir.newFile("logBloom-0.cache");

    createLogBloomCache(logBloom);

    createBlock(3L);

    assertThat(logBloom.length()).isEqualTo(BLOOM_BITS_LENGTH * 3);

    when(blockchain.getBlockHeader(anyLong())).thenReturn(Optional.of(fakeHeader));
    transactionLogBloomCacher.cacheLogsBloomForBlockHeader(
        blockchain.getBlockHeader(NUMBER_3).get(), Optional.empty(), Optional.of(logBloom));

    assertThat(logBloom.length()).isEqualTo(BLOOM_BITS_LENGTH * 4);
    assertThat(cacheDir.getRoot().list().length).isEqualTo(1);

    blockchainQueries.matchingLogs(NUMBER_3, 3, logsQuery, () -> true);

    verify(blockchain, times(1)).getBlockHashByNumber(NUMBER_3);
    verify(blockchain, times(1)).getBlockHeader(NUMBER_3);
    verify(blockchain, times(1)).getBlockHeader(testBlockHeaderHash);
    verify(blockchain, times(1)).getTxReceipts(testBlockHeaderHash);
    verify(blockchain, times(1)).getBlockBody(testBlockHeaderHash);
    verify(blockchain, times(1)).blockIsOnCanonicalChain(testBlockHeaderHash);

    verifyNoMoreInteractions(blockchain);
  }

  private void createLogBloomCache(final File logBloom) throws IOException {
    try (final RandomAccessFile randomAccessFile = new RandomAccessFile(logBloom, "rws")) {
      randomAccessFile.write(testLogsBloomFilter.toArray());
      randomAccessFile.write(testLogsBloomFilter.toArray());
      randomAccessFile.write(testLogsBloomFilter.toArray());
    }
  }

  private BlockHeader createBlock(final long number) {
    final Log testLog = new Log(testAddress, testMessage, List.of());
    final BlockHeader fakeHeader =
        new BlockHeader(
            Hash.EMPTY,
            Hash.EMPTY,
            Address.ZERO,
            Hash.EMPTY,
            Hash.EMPTY,
            Hash.EMPTY,
            LogsBloomFilter.empty(),
            Difficulty.ZERO,
            number,
            0,
            0,
            0,
            Bytes.EMPTY,
            null,
            Hash.EMPTY,
            0,
            new MainnetBlockHeaderFunctions(),
            Optional.of(testLogsBloomFilter));
    return fakeHeader;
  }
}
