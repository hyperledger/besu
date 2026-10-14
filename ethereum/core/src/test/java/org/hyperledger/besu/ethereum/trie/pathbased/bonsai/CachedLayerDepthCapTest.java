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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.worldstate.WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead;
import static org.hyperledger.besu.ethereum.worldstate.WorldStateQueryParams.withBlockHeaderAndUpdateNodeHead;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.BlockExecutionContext;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.cache.PathBasedWorldStateCacheManager;
import org.hyperledger.besu.plugin.services.worldstate.MutableWorldState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Blocks imported on top of each other without a fork choice moving the chain head. */
public class CachedLayerDepthCapTest extends AbstractIsolationTests {

  private static final int MAX_DEPTH = 4;

  private long nextNonce = 0;
  private int nextRecipient = 1;

  @Test
  void layersStackUpWithoutTheCap() {
    archive.setMaxCachedLayerDepth(Integer.MAX_VALUE);

    final List<Block> imported = importWithoutForkChoice(blockchain.getChainHeadHeader(), 20);

    assertThat(cachedLayerDepth(imported.getLast().getHeader())).isGreaterThan(MAX_DEPTH * 4);
  }

  @Test
  void capPersistsTheWorldStateAheadOfTheChainHead() {
    archive.setMaxCachedLayerDepth(MAX_DEPTH);
    final BlockHeader chainHead = blockchain.getChainHeadHeader();

    final List<Block> imported = importWithoutForkChoice(chainHead, 20);

    assertThat(blockchain.getChainHeadHash()).isEqualTo(chainHead.getHash());
    assertThat(archive.isHeadWorldStateMovedByDepthCap()).isTrue();
    final BlockHeader persisted =
        blockchain
            .getBlockHeader(archive.getWorldStateKeyValueStorage().getWorldStateBlockHash().get())
            .orElseThrow();
    assertThat(imported.stream().map(Block::getHash)).contains(persisted.getHash());
    assertThat(archive.getWorldState().rootHash()).isEqualTo(persisted.getStateRoot());
    assertThat(cachedLayerDepth(imported.getLast().getHeader())).isLessThanOrEqualTo(MAX_DEPTH);
  }

  @Test
  void chainHeadStateStaysReadableAfterTheCap() {
    archive.setMaxCachedLayerDepth(MAX_DEPTH);
    final BlockHeader chainHead = blockchain.getChainHeadHeader();

    importWithoutForkChoice(chainHead, 20);

    assertThat(archive.getWorldStateCacheManager().contains(chainHead.getHash())).isTrue();
    assertThat(
            archive
                .getWorldState(withBlockHeaderAndNoUpdateNodeHead(chainHead))
                .map(MutableWorldState::rootHash))
        .contains(chainHead.getStateRoot());
  }

  @Test
  void forkChoiceMovesTheCappedWorldStateToTheImportedTip() {
    archive.setMaxCachedLayerDepth(MAX_DEPTH);
    final List<Block> imported = importWithoutForkChoice(blockchain.getChainHeadHeader(), 20);
    final BlockHeader tip = imported.getLast().getHeader();

    assertThat(
            archive
                .getWorldState(withBlockHeaderAndUpdateNodeHead(tip))
                .map(MutableWorldState::rootHash))
        .contains(tip.getStateRoot());
    assertThat(archive.isHeadWorldStateMovedByDepthCap()).isFalse();
    assertThat(archive.getWorldStateKeyValueStorage().getWorldStateBlockHash())
        .contains(tip.getHash());
  }

  @Test
  void chainHeadStateStaysCachedBeyondTheRetainedLayers() {
    archive.setMaxCachedLayerDepth(Integer.MAX_VALUE);
    final BlockHeader chainHead = blockchain.getChainHeadHeader();

    importWithoutForkChoice(chainHead, (int) PathBasedWorldStateCacheManager.RETAINED_LAYERS + 4);

    assertThat(archive.getWorldStateCacheManager().contains(chainHead.getHash())).isTrue();
  }

  private int cachedLayerDepth(final BlockHeader blockHeader) {
    return archive.getWorldStateCacheManager().getCachedLayerDepth(blockHeader);
  }

  /** Imports blocks the way a payload is, each on the previous one, without moving the head. */
  private List<Block> importWithoutForkChoice(final BlockHeader parent, final int count) {
    final List<Block> blocks = new ArrayList<>();
    BlockHeader parentHeader = parent;
    for (int i = 0; i < count; i++) {
      archive.capCachedLayerDepth(parentHeader);
      final Block block = nextBlock(parentHeader);
      final BlockProcessingResult result;
      try (MutableWorldState worldState =
          archive.getWorldState(withBlockHeaderAndNoUpdateNodeHead(parentHeader)).orElseThrow()) {
        result =
            protocolSchedule
                .getByBlockHeader(block.getHeader())
                .getBlockProcessor()
                .processBlock(
                    BlockExecutionContext.builder()
                        .protocolContext(protocolContext)
                        .worldState(worldState)
                        .block(block)
                        .blockAccessList(Optional.empty())
                        .build());
      } catch (final Exception e) {
        throw new IllegalStateException(e);
      }
      assertThat(result.isSuccessful()).isTrue();
      blockchain.storeBlock(block, result.getReceipts(), Optional.empty());
      blocks.add(block);
      parentHeader = block.getHeader();
    }
    return blocks;
  }

  private Block nextBlock(final BlockHeader parent) {
    final Address recipient = Address.fromHexString(String.format("0x%040x", nextRecipient++));
    return forTransactions(List.of(burnTransaction(sender1, nextNonce++, recipient)), parent);
  }
}
