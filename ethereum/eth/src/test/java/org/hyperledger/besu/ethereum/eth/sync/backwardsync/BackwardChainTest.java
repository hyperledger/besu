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

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class BackwardChainTest {

  public static final int HEIGHT = 20_000;
  public static final int ELEMENTS = 20;
  private List<Block> blocks;

  @Before
  public void prepareData() {
    blocks = prepareChain(ELEMENTS, HEIGHT);
  }

  @Test
  public void shouldReturnFirstHeaderCorrectly() {
    BackwardChain backwardChain = new BackwardChain(blocks.get(blocks.size() - 1));
    backwardChain.saveHeader(blocks.get(blocks.size() - 2).getHeader());
    backwardChain.saveHeader(blocks.get(blocks.size() - 3).getHeader());
    backwardChain.saveHeader(blocks.get(blocks.size() - 4).getHeader());
    BlockHeader firstHeader = backwardChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 4).getHeader());
  }

  @Test
  public void shouldSaveHeadersWhenHeightAndHashMatches() {
    BackwardChain backwardChain = new BackwardChain(blocks.get(blocks.size() - 1));
    backwardChain.saveHeader(blocks.get(blocks.size() - 2).getHeader());
    backwardChain.saveHeader(blocks.get(blocks.size() - 3).getHeader());
    backwardChain.saveHeader(blocks.get(blocks.size() - 4).getHeader());
    BlockHeader firstHeader = backwardChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 4).getHeader());
  }

  @Test
  public void shouldNotSaveHeadersWhenWrongHeight() {
    BackwardChain backwardChain = new BackwardChain(blocks.get(blocks.size() - 1));
    backwardChain.saveHeader(blocks.get(blocks.size() - 2).getHeader());
    backwardChain.saveHeader(blocks.get(blocks.size() - 3).getHeader());
    assertThatThrownBy(() -> backwardChain.saveHeader(blocks.get(blocks.size() - 5).getHeader()))
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("Wrong height of header");
    BlockHeader firstHeader = backwardChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 3).getHeader());
  }

  @Test
  public void shouldNotSaveHeadersWhenWrongHash() {
    BackwardChain backwardChain = new BackwardChain(blocks.get(blocks.size() - 1));
    backwardChain.saveHeader(blocks.get(blocks.size() - 2).getHeader());
    backwardChain.saveHeader(blocks.get(blocks.size() - 3).getHeader());
    BlockHeader wrongHashHeader = prepareWrongParentHash(blocks.get(blocks.size() - 4).getHeader());
    assertThatThrownBy(() -> backwardChain.saveHeader(wrongHashHeader))
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("Hash of header does not match our expectations");
    BlockHeader firstHeader = backwardChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 3).getHeader());
  }

  @Test
  public void shouldMergeConnectedChains() {

    BackwardChain firstChain = new BackwardChain(blocks.get(blocks.size() - 1));
    firstChain.saveHeader(blocks.get(blocks.size() - 2).getHeader());
    firstChain.saveHeader(blocks.get(blocks.size() - 3).getHeader());

    BackwardChain secondChain = new BackwardChain(blocks.get(blocks.size() - 4));
    secondChain.saveHeader(blocks.get(blocks.size() - 5).getHeader());
    secondChain.saveHeader(blocks.get(blocks.size() - 6).getHeader());

    BlockHeader firstHeader = firstChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 3).getHeader());

    firstChain.merge(secondChain);

    firstHeader = firstChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 6).getHeader());
  }

  @Test
  public void shouldNotMergeNotConnectedChains() {

    BackwardChain firstChain = new BackwardChain(blocks.get(blocks.size() - 1));
    firstChain.saveHeader(blocks.get(blocks.size() - 2).getHeader());
    firstChain.saveHeader(blocks.get(blocks.size() - 3).getHeader());

    BackwardChain secondChain = new BackwardChain(blocks.get(blocks.size() - 5));
    secondChain.saveHeader(blocks.get(blocks.size() - 6).getHeader());
    secondChain.saveHeader(blocks.get(blocks.size() - 7).getHeader());

    BlockHeader firstHeader = firstChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 3).getHeader());

    firstChain.merge(secondChain);

    firstHeader = firstChain.getFirstAncestorHeader().orElseThrow();
    assertThat(firstHeader).isEqualTo(blocks.get(blocks.size() - 3).getHeader());
  }

  @Test
  public void shouldDropFromTheEnd() {

    BackwardChain backwardChain = new BackwardChain(blocks.get(blocks.size() - 1));
    backwardChain.saveHeader(blocks.get(blocks.size() - 2).getHeader());
    backwardChain.saveHeader(blocks.get(blocks.size() - 3).getHeader());

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
