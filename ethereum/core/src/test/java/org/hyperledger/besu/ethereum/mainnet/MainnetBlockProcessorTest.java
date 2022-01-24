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
package org.hyperledger.besu.ethereum.mainnet;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestWorldState;

import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MainnetBlockProcessorTest {

  private final MainnetTransactionProcessor transactionProcessor =
      mock(MainnetTransactionProcessor.class);
  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory =
      mock(AbstractBlockProcessor.TransactionReceiptFactory.class);

  @Test
  public void noAccountCreatedWhenBlockRewardIsZeroAndSkipped() {
    final Blockchain blockchain = new ReferenceTestBlockchain();
    final MainnetBlockProcessor blockProcessor =
        new MainnetBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            Wei.ZERO,
            BlockHeader::getCoinbase,
            true,
            Optional.empty());

    final MutableWorldState worldState = ReferenceTestWorldState.create(emptyMap());
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
    final Blockchain blockchain = new ReferenceTestBlockchain();
    final MainnetBlockProcessor blockProcessor =
        new MainnetBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            Wei.ZERO,
            BlockHeader::getCoinbase,
            false,
            Optional.empty());

    final MutableWorldState worldState = ReferenceTestWorldState.create(emptyMap());
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
