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
package org.hyperledger.besu.ethereum.eth.sync.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockBody;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The import step against a real blockchain, with the batches arriving in any order. */
public class ImportSyncBlocksOutOfOrderTest {

  private static final int PIVOT = 9;

  // the index of a block is its number
  private final List<Block> chain = new BlockDataGenerator(1).blockSequence(PIVOT + 1);
  private final MutableBlockchain blockchain = createInMemoryBlockchain(chain.getFirst());
  private final ProtocolContext protocolContext = mock(ProtocolContext.class);
  private ImportSyncBlocksStep importStep;

  @BeforeEach
  public void setUp() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    // the headers are downloaded before the bodies
    blockchain.storeBlockHeaders(
        chain.subList(1, PIVOT + 1).stream().map(Block::getHeader).toList());
    importStep =
        new ImportSyncBlocksStep(
            protocolContext, null, mock(SyncState.class), 0L, PIVOT, false, Optional.empty());
  }

  @Test
  public void shouldLeaveTheChainHeadBehindABatchThatIsMissing() {
    importStep.accept(batch(4, 6));
    importStep.accept(batch(7, 9));

    assertThat(blockchain.getChainHeadBlockNumber()).isZero();
    // what arrived is stored all the same
    assertThat(blockchain.getBlockBody(chain.get(5).getHash())).isPresent();
    assertThat(blockchain.getBlockBody(chain.get(9).getHash())).isPresent();
    assertThat(blockchain.getBlockBody(chain.get(2).getHash())).isEmpty();
  }

  @Test
  public void shouldEndUpWithTheSameChainWhateverTheOrderOfTheBatches() {
    final MutableBlockchain importedInOrder = createInMemoryBlockchain(chain.getFirst());
    importedInOrder.storeBlockHeaders(
        chain.subList(1, PIVOT + 1).stream().map(Block::getHeader).toList());
    importedInOrder.unsafeImportSyncBodiesAndReceipts(batch(1, 9), false);

    importStep.accept(batch(7, 9));
    importStep.accept(batch(1, 3));
    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(3);
    importStep.accept(batch(4, 6));

    final BlockHeader pivotHeader = chain.get(PIVOT).getHeader();
    assertThat(blockchain.getChainHeadHeader()).isEqualTo(pivotHeader);
    assertThat(blockchain.getChainHeadHeader()).isEqualTo(importedInOrder.getChainHeadHeader());
    for (int number = 1; number <= PIVOT; number++) {
      final BlockHeader header = chain.get(number).getHeader();
      assertThat(blockchain.getBlockHashByNumber(number)).contains(header.getHash());
      assertThat(blockchain.getBlockBody(header.getHash())).isPresent();
      final Optional<Difficulty> expectedTotalDifficulty =
          importedInOrder.getTotalDifficultyByHash(header.getHash());
      assertThat(expectedTotalDifficulty).isPresent();
      assertThat(blockchain.getTotalDifficultyByHash(header.getHash()))
          .isEqualTo(expectedTotalDifficulty);
    }
  }

  /** The blocks with the given numbers, as a batch of the download. */
  private List<SyncBlockWithReceipts> batch(final int firstBlock, final int lastBlock) {
    return chain.subList(firstBlock, lastBlock + 1).stream()
        .map(
            block ->
                new SyncBlockWithReceipts(
                    new SyncBlock(
                        block.getHeader(),
                        SyncBlockBody.emptyWithNullWithdrawals(
                            ProtocolScheduleFixture.TESTING_NETWORK)),
                    Collections.emptyList()))
        .toList();
  }
}
