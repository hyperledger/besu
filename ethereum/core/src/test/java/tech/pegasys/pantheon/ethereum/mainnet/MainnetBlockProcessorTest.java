/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockProcessor.TransactionReceiptFactory;
import tech.pegasys.pantheon.ethereum.vm.TestBlockchain;
import tech.pegasys.pantheon.ethereum.vm.WorldStateMock;

import org.junit.Test;

public class MainnetBlockProcessorTest {

  private final TransactionProcessor transactionProcessor = mock(TransactionProcessor.class);
  private final TransactionReceiptFactory transactionReceiptFactory =
      mock(TransactionReceiptFactory.class);

  @Test
  public void noAccountCreatedWhenBlockRewardIsZeroAndSkipped() {
    final Blockchain blockchain = new TestBlockchain();
    final MainnetBlockProcessor blockProcessor =
        new MainnetBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            Wei.ZERO,
            BlockHeader::getCoinbase,
            true);

    final MutableWorldState worldState = WorldStateMock.create(emptyMap());
    final Hash initialHash = worldState.rootHash();

    final BlockHeader emptyBlockHeader =
        new BlockHeaderTestFixture()
            .transactionsRoot(Hash.EMPTY_LIST_HASH)
            .ommersHash(Hash.EMPTY_LIST_HASH)
            .buildHeader();
    blockProcessor.processBlock(blockchain, worldState, emptyBlockHeader, emptyList(), emptyList());

    // An empty block with 0 reward should not change the world state
    assertThat(worldState.rootHash()).isEqualTo(initialHash);
  }

  @Test
  public void accountCreatedWhenBlockRewardIsZeroAndNotSkipped() {
    final Blockchain blockchain = new TestBlockchain();
    final MainnetBlockProcessor blockProcessor =
        new MainnetBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            Wei.ZERO,
            BlockHeader::getCoinbase,
            false);

    final MutableWorldState worldState = WorldStateMock.create(emptyMap());
    final Hash initialHash = worldState.rootHash();

    final BlockHeader emptyBlockHeader =
        new BlockHeaderTestFixture()
            .transactionsRoot(Hash.EMPTY_LIST_HASH)
            .ommersHash(Hash.EMPTY_LIST_HASH)
            .buildHeader();
    blockProcessor.processBlock(blockchain, worldState, emptyBlockHeader, emptyList(), emptyList());

    // An empty block with 0 reward should change the world state prior to EIP158
    assertThat(worldState.rootHash()).isNotEqualTo(initialHash);
  }
}
