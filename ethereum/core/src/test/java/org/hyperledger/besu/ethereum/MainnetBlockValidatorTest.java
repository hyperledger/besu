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
package org.hyperledger.besu.ethereum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class MainnetBlockValidatorTest {

  private final BlockHeaderValidator blockHeaderValidator = mock(BlockHeaderValidator.class);
  private final BlockBodyValidator blockBodyValidator = mock(BlockBodyValidator.class);
  private final BlockProcessor blockProcessor = mock(BlockProcessor.class);
  private final ProtocolContext protocolContext = mock(ProtocolContext.class);
  protected final MutableBlockchain blockchain = mock(MutableBlockchain.class);
  protected final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  private final BadBlockManager badBlockManager = new BadBlockManager();

  private MainnetBlockValidator mainnetBlockValidator;
  private Block badBlock;

  @Before
  public void setup() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(protocolContext.getWorldStateArchive()).thenReturn(worldStateArchive);
    mainnetBlockValidator =
        new MainnetBlockValidator(
            blockHeaderValidator, blockBodyValidator, blockProcessor, badBlockManager);
    badBlock =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockNumber(2)
                    .hasTransactions(false)
                    .setBlockHeaderFunctions(new MainnetBlockHeaderFunctions()));
  }

  @Test
  public void shouldDetectAndCacheInvalidBlocksWhenParentBlockNotPresent() {
    when(blockchain.getBlockHeader(anyLong())).thenReturn(Optional.empty());
    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(0);
    mainnetBlockValidator.validateAndProcessBlock(
        protocolContext,
        badBlock,
        HeaderValidationMode.DETACHED_ONLY,
        HeaderValidationMode.DETACHED_ONLY);
    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(1);
  }

  @Test
  public void shouldDetectAndCacheInvalidBlocksWhenHeaderInvalid() {
    when(blockchain.getBlockHeader(any(Hash.class)))
        .thenReturn(Optional.of(new BlockHeaderTestFixture().buildHeader()));
    when(blockHeaderValidator.validateHeader(
            any(BlockHeader.class),
            any(BlockHeader.class),
            eq(protocolContext),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenReturn(false);

    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(0);
    mainnetBlockValidator.validateAndProcessBlock(
        protocolContext,
        badBlock,
        HeaderValidationMode.DETACHED_ONLY,
        HeaderValidationMode.DETACHED_ONLY);
    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(1);
  }

  @Test
  public void shouldDetectAndCacheInvalidBlocksWhenParentWorldStateNotAvailable() {
    when(blockchain.getBlockHeader(any(Hash.class)))
        .thenReturn(Optional.of(new BlockHeaderTestFixture().buildHeader()));
    when(blockHeaderValidator.validateHeader(
            any(BlockHeader.class),
            any(BlockHeader.class),
            eq(protocolContext),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenReturn(true);
    when(worldStateArchive.getMutable(any(Hash.class), any(Hash.class)))
        .thenReturn(Optional.empty());

    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(0);
    mainnetBlockValidator.validateAndProcessBlock(
        protocolContext,
        badBlock,
        HeaderValidationMode.DETACHED_ONLY,
        HeaderValidationMode.DETACHED_ONLY);
    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(1);
  }

  @Test
  public void shouldDetectAndCacheInvalidBlocksWhenProcessBlockFailed() {
    when(blockchain.getBlockHeader(any(Hash.class)))
        .thenReturn(Optional.of(new BlockHeaderTestFixture().buildHeader()));
    when(blockHeaderValidator.validateHeader(
            any(BlockHeader.class),
            any(BlockHeader.class),
            eq(protocolContext),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenReturn(true);
    when(worldStateArchive.getMutable(any(Hash.class), any(Hash.class)))
        .thenReturn(Optional.of(mock(MutableWorldState.class)));
    when(blockProcessor.processBlock(eq(blockchain), any(MutableWorldState.class), eq(badBlock)))
        .thenReturn(
            new BlockProcessor.Result() {
              @SuppressWarnings("unchecked")
              @Override
              public List<TransactionReceipt> getReceipts() {
                return Collections.EMPTY_LIST;
              }

              @SuppressWarnings("unchecked")
              @Override
              public List<TransactionReceipt> getPrivateReceipts() {
                return Collections.EMPTY_LIST;
              }

              @Override
              public boolean isSuccessful() {
                return false;
              }
            });
    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(0);
    mainnetBlockValidator.validateAndProcessBlock(
        protocolContext,
        badBlock,
        HeaderValidationMode.DETACHED_ONLY,
        HeaderValidationMode.DETACHED_ONLY);
    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(1);
  }

  @Test
  public void shouldDetectAndCacheInvalidBlocksWhenBodyInvalid() {
    when(blockchain.getBlockHeader(any(Hash.class)))
        .thenReturn(Optional.of(new BlockHeaderTestFixture().buildHeader()));
    when(blockHeaderValidator.validateHeader(
            any(BlockHeader.class),
            any(BlockHeader.class),
            eq(protocolContext),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenReturn(true);
    when(worldStateArchive.getMutable(any(Hash.class), any(Hash.class)))
        .thenReturn(Optional.of(mock(MutableWorldState.class)));
    when(blockProcessor.processBlock(eq(blockchain), any(MutableWorldState.class), eq(badBlock)))
        .thenReturn(
            new BlockProcessor.Result() {
              @SuppressWarnings("unchecked")
              @Override
              public List<TransactionReceipt> getReceipts() {
                return Collections.EMPTY_LIST;
              }

              @SuppressWarnings("unchecked")
              @Override
              public List<TransactionReceipt> getPrivateReceipts() {
                return Collections.EMPTY_LIST;
              }

              @Override
              public boolean isSuccessful() {
                return true;
              }
            });
    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(0);
    mainnetBlockValidator.validateAndProcessBlock(
        protocolContext,
        badBlock,
        HeaderValidationMode.DETACHED_ONLY,
        HeaderValidationMode.DETACHED_ONLY);
    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(1);
  }

  @Test
  public void shouldNotCacheWhenValidBlocks() {
    when(blockchain.getBlockHeader(any(Hash.class)))
        .thenReturn(Optional.of(new BlockHeaderTestFixture().buildHeader()));
    when(blockHeaderValidator.validateHeader(
            any(BlockHeader.class),
            any(BlockHeader.class),
            eq(protocolContext),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenReturn(true);
    when(worldStateArchive.getMutable(any(Hash.class), any(Hash.class)))
        .thenReturn(Optional.of(mock(MutableWorldState.class)));
    when(blockProcessor.processBlock(eq(blockchain), any(MutableWorldState.class), eq(badBlock)))
        .thenReturn(
            new BlockProcessor.Result() {
              @SuppressWarnings("unchecked")
              @Override
              public List<TransactionReceipt> getReceipts() {
                return Collections.EMPTY_LIST;
              }

              @SuppressWarnings("unchecked")
              @Override
              public List<TransactionReceipt> getPrivateReceipts() {
                return Collections.EMPTY_LIST;
              }

              @Override
              public boolean isSuccessful() {
                return true;
              }
            });
    when(blockBodyValidator.validateBody(
            eq(protocolContext),
            eq(badBlock),
            any(),
            any(),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenReturn(true);
    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(0);
    mainnetBlockValidator.validateAndProcessBlock(
        protocolContext,
        badBlock,
        HeaderValidationMode.DETACHED_ONLY,
        HeaderValidationMode.DETACHED_ONLY);
    assertThat(badBlockManager.getBadBlocks()).isEmpty();
  }

  @Test
  public void shouldReturnBadBlockBasedOnTheHash() {
    when(blockchain.getBlockHeader(any(Hash.class)))
        .thenReturn(Optional.of(new BlockHeaderTestFixture().buildHeader()));
    when(blockHeaderValidator.validateHeader(
            any(BlockHeader.class),
            any(BlockHeader.class),
            eq(protocolContext),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenReturn(false);
    mainnetBlockValidator.validateAndProcessBlock(
        protocolContext,
        badBlock,
        HeaderValidationMode.DETACHED_ONLY,
        HeaderValidationMode.DETACHED_ONLY);
    assertThat(badBlockManager.getBadBlock(badBlock.getHash())).containsSame(badBlock);
  }
}
