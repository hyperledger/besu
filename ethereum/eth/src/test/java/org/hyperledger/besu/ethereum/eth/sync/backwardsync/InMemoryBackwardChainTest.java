/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.eth.sync.backwardsync.ChainForTestCreator.prepareChain;
import static org.hyperledger.besu.ethereum.eth.sync.backwardsync.ChainForTestCreator.prepareWrongParentHash;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InMemoryBackwardChainTest {

  public static final int HEIGHT = 20_000;
  public static final int ELEMENTS = 20;
  private List<Block> blocks;

  GenericKeyValueStorageFacade<Hash, BlockHeader> headersStorage;
  GenericKeyValueStorageFacade<Hash, Block> blocksStorage;

  @Before
  public void prepareData() {
    headersStorage =
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe,
            new BlocksHeadersConvertor(new MainnetBlockHeaderFunctions()),
            new InMemoryKeyValueStorage());
    blocksStorage =
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe,
            new BlocksConvertor(new MainnetBlockHeaderFunctions()),
            new InMemoryKeyValueStorage());

    blocks = prepareChain(ELEMENTS, HEIGHT);
  }

  @Test
  public void shouldReturnFirstHeaderCorrectly() {
    BackwardChain backwardChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(blocks.size() - 1));
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 2).getHeader());
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 3).getHeader());
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 4).getHeader());
    BlockHeader firstHeader = backwardChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 4).getHeader());
  }

  @Test
  public void shouldSaveHeadersWhenHeightAndHashMatches() {
    BackwardChain backwardChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(blocks.size() - 1));
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 2).getHeader());
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 3).getHeader());
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 4).getHeader());
    BlockHeader firstHeader = backwardChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 4).getHeader());
  }

  @Test
  public void shouldNotSaveHeadersWhenWrongHeight() {
    BackwardChain backwardChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(blocks.size() - 1));
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 2).getHeader());
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 3).getHeader());
    assertThatThrownBy(
            () -> backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 5).getHeader()))
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("Wrong height of header");
    BlockHeader firstHeader = backwardChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 3).getHeader());
  }

  @Test
  public void shouldNotSaveHeadersWhenWrongHash() {
    BackwardChain backwardChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(blocks.size() - 1));
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 2).getHeader());
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 3).getHeader());
    BlockHeader wrongHashHeader = prepareWrongParentHash(blocks.get(blocks.size() - 4).getHeader());
    assertThatThrownBy(() -> backwardChain.prependAncestorsHeader(wrongHashHeader))
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("Hash of header does not match our expectations");
    BlockHeader firstHeader = backwardChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 3).getHeader());
  }

  @Test
  public void shouldMergeConnectedChains() {

    BackwardChain firstChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(blocks.size() - 1));
    firstChain.prependAncestorsHeader(blocks.get(blocks.size() - 2).getHeader());
    firstChain.prependAncestorsHeader(blocks.get(blocks.size() - 3).getHeader());

    BackwardChain secondChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(blocks.size() - 4));
    secondChain.prependAncestorsHeader(blocks.get(blocks.size() - 5).getHeader());
    secondChain.prependAncestorsHeader(blocks.get(blocks.size() - 6).getHeader());

    BlockHeader firstHeader = firstChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 3).getHeader());

    firstChain.prependChain(secondChain);

    firstHeader = firstChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 6).getHeader());
  }

  @Test
  public void shouldNotMergeNotConnectedChains() {

    BackwardChain firstChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(blocks.size() - 1));
    firstChain.prependAncestorsHeader(blocks.get(blocks.size() - 2).getHeader());
    firstChain.prependAncestorsHeader(blocks.get(blocks.size() - 3).getHeader());

    BackwardChain secondChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(blocks.size() - 5));
    secondChain.prependAncestorsHeader(blocks.get(blocks.size() - 6).getHeader());
    secondChain.prependAncestorsHeader(blocks.get(blocks.size() - 7).getHeader());

    BlockHeader firstHeader = firstChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 3).getHeader());

    firstChain.prependChain(secondChain);

    firstHeader = firstChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 3).getHeader());
  }

  @Test
  public void shouldDropFromTheEnd() {

    BackwardChain backwardChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(blocks.size() - 1));
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 2).getHeader());
    backwardChain.prependAncestorsHeader(blocks.get(blocks.size() - 3).getHeader());

    BlockHeader firstHeader = backwardChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 3).getHeader());

    backwardChain.dropFirstHeader();

    firstHeader = backwardChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 2).getHeader());

    backwardChain.dropFirstHeader();

    firstHeader = backwardChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 1).getHeader());
  }
}
