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

package org.hyperledger.besu.ethereum.api.query;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GoQuorumPrivateTxBloomBlockchainQueriesTest {

  @ClassRule public static TemporaryFolder cacheDir = new TemporaryFolder();

  private static LogsQuery logsQuery;
  private Hash testHash;
  private static LogsBloomFilter testLogsBloomFilter;

  @Mock MutableBlockchain blockchain;
  @Mock WorldStateArchive worldStateArchive;
  @Mock EthScheduler scheduler;
  private BlockchainQueries blockchainQueries;

  @BeforeClass
  public static void setupClass() {
    final Address testAddress = Address.fromHexString("0x123456");
    final Bytes testMessage = Bytes.fromHexString("0x9876");
    final Log testLog = new Log(testAddress, testMessage, List.of());
    testLogsBloomFilter = LogsBloomFilter.builder().insertLog(testLog).build();
    logsQuery = new LogsQuery(List.of(testAddress), List.of());
  }

  @Before
  public void setup() {
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
            0,
            0,
            0,
            0,
            Bytes.EMPTY,
            null,
            Hash.EMPTY,
            0,
            new MainnetBlockHeaderFunctions(),
            Optional.of(testLogsBloomFilter));
    testHash = fakeHeader.getHash();
    final BlockBody fakeBody = new BlockBody(Collections.emptyList(), Collections.emptyList());
    when(blockchain.getBlockHeader(any())).thenReturn(Optional.of(fakeHeader));
    when(blockchain.getBlockHeader(anyLong())).thenReturn(Optional.of(fakeHeader));
    when(blockchain.getTxReceipts(any())).thenReturn(Optional.of(Collections.emptyList()));
    when(blockchain.getBlockBody(any())).thenReturn(Optional.of(fakeBody));
    blockchainQueries =
        new BlockchainQueries(
            blockchain,
            worldStateArchive,
            Optional.of(cacheDir.getRoot().toPath()),
            Optional.of(scheduler));
  }

  /**
   * Tests whether block headers containing private blooms match. The resulting list of
   * LogWithMetadata would be empty, because the mocked blockchain does not return any receipts, but
   * we do check that the methods on the blockchain are actually called that would be called if the
   * block header matches the bloom filter.
   */
  @Test
  public void testPrivateBloomsWork() {
    blockchainQueries.matchingLogs(0, 2, logsQuery, () -> true);

    verify(blockchain, times(3)).getBlockHeader(anyLong());
    verify(blockchain, times(3)).getBlockHeader(testHash);
    verify(blockchain, times(3)).getTxReceipts(testHash);
    verify(blockchain, times(3)).getBlockBody(testHash);
    verify(blockchain, times(3)).blockIsOnCanonicalChain(testHash);

    verifyNoMoreInteractions(blockchain);
  }
}
