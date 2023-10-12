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

package org.hyperledger.besu.ethereum.bonsai.trielog;

import static org.mockito.Mockito.times;

import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.chain.Blockchain;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class TrieLogPrunerTest {

  private BonsaiWorldStateKeyValueStorage rootWorldStateStorage;
  private Blockchain blockchain;

  @BeforeEach
  public void setup() {
    rootWorldStateStorage = Mockito.mock(BonsaiWorldStateKeyValueStorage.class);
    blockchain = Mockito.mock(Blockchain.class);
  }

  @SuppressWarnings("BannedMethod")
  @Test
  public void trieLogs_pruned_in_reverse_order_within_pruning_window() {
    Configurator.setLevel(LogManager.getLogger(TrieLogPruner.class).getName(), Level.TRACE);

    // Given

    // pruning window is below numBlocksToRetain and inside the pruningWindowSize offset.
    final long blocksToRetain = 3;
    final int pruningWindowSize = 2;
    TrieLogPruner trieLogPruner =
        new TrieLogPruner(rootWorldStateStorage, blockchain, blocksToRetain, pruningWindowSize);

    final byte[] key0 = new byte[] {1, 2, 3}; // older block outside the prune window
    final byte[] key1 = new byte[] {1, 2, 3}; // block inside the prune window
    final byte[] key2 = new byte[] {4, 5, 6}; // same block (fork)
    final byte[] key3 = new byte[] {7, 8, 9}; // different block inside the prune window
    final byte[] key4 = new byte[] {10, 11, 12}; // retained block
    final byte[] key5 = new byte[] {13, 14, 15}; // different retained block
    final byte[] key6 = new byte[] {7, 8, 9}; // another retained block
    final long block0 = 1000L;
    final long block1 = 1001L;
    final long block2 = 1002L;
    final long block3 = 1003L;
    final long block4 = 1004L;
    final long block5 = 1005L;

    trieLogPruner.rememberTrieLogKeyForPruning(block0, key0); // older block outside prune window
    trieLogPruner.rememberTrieLogKeyForPruning(block1, key1); // block inside the prune window
    trieLogPruner.rememberTrieLogKeyForPruning(block1, key2); // same block number (fork)
    trieLogPruner.rememberTrieLogKeyForPruning(block2, key3); // different block inside prune window
    trieLogPruner.rememberTrieLogKeyForPruning(block3, key4); // retained block
    trieLogPruner.rememberTrieLogKeyForPruning(block4, key5); // different retained block
    trieLogPruner.rememberTrieLogKeyForPruning(block5, key6); // another retained block

    Mockito.when(blockchain.getChainHeadBlockNumber()).thenReturn(block5);

    // When
    trieLogPruner.prune();

    // Then
    InOrder inOrder = Mockito.inOrder(rootWorldStateStorage);
    inOrder.verify(rootWorldStateStorage, times(1)).pruneTrieLog(key3);
    inOrder.verify(rootWorldStateStorage, times(1)).pruneTrieLog(key1);
    inOrder.verify(rootWorldStateStorage, times(1)).pruneTrieLog(key2);

    // Subsequent run should add one more block, then prune two oldest remaining keys
    long block6 = 1006L;
    trieLogPruner.rememberTrieLogKeyForPruning(block6, new byte[] {1, 2, 3});
    Mockito.when(blockchain.getChainHeadBlockNumber()).thenReturn(block6);

    trieLogPruner.prune();

    inOrder.verify(rootWorldStateStorage, times(1)).pruneTrieLog(key4);
    inOrder.verify(rootWorldStateStorage, times(1)).pruneTrieLog(key0);
  }
}
