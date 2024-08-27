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
package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.worldview.VerkleWorldState;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransitionTests {

  final InMemoryKeyValueStorageProvider storageProvider = new InMemoryKeyValueStorageProvider();
  @Mock
  Blockchain blockchain;

  @Mock
  BlockHeader mockHeader;

  @Mock
  BesuContext besuContext;
  @Test
  public void shouldBeBeforeTransition() {
    long now = System.currentTimeMillis();
    VerkleTransitionContext ctx = new VerkleTransitionContext(now + 15000L);
    assertThat(ctx.isBeforeTransition()).isTrue();
    assertThat(ctx.isBeforeTransition(now)).isTrue();
    assertThat(
            ctx.isVerkleForMutation(
                BlockHeaderBuilder.createDefault().timestamp(now).buildBlockHeader()))
        .isFalse();

    var archive = getArchive(ctx);
    assertThat(archive).isNotNull();
    assertThat(archive.getMutable()).isNotNull();
    assertThat(archive.getMutable()).isInstanceOf(BonsaiWorldState.class);

  }

  @Test
  public void shouldBeOnTransitionBlock() {
    long now = System.currentTimeMillis();
    VerkleTransitionContext ctx = new VerkleTransitionContext(now + 5000L);
    // assert we are _BEFORE_ transition timestamp
    assertThat(ctx.isBeforeTransition()).isTrue();
    assertThat(ctx.isBeforeTransition(now)).isTrue();

    // assert that a block built right now should be mutating using verkle state
    assertThat(
        ctx.isVerkleForMutation(
            BlockHeaderBuilder.createDefault().timestamp(now).buildBlockHeader()))
        .isTrue();

    //  assert we return a mutable verkle worldstate for the that block preceded transition
    when(mockHeader.getHash()).thenReturn(Hash.ZERO);
    when(mockHeader.getBlockHash()).thenReturn(Hash.ZERO);
    when(mockHeader.getStateRoot()).thenReturn(Hash.ZERO);
    when(mockHeader.getNumber()).thenReturn(0L);
    when(mockHeader.getTimestamp()).thenReturn(now);
    when(blockchain.getBlockHeader(any()))
        .thenReturn(Optional.of(mockHeader));
    var archive = getArchive(ctx);
    assertThat(archive).isNotNull();
    assertThat(archive.getMutable()).isNotNull();
    var mockWorld = archive.getMutable(Hash.ZERO, Hash.ZERO);
    assertThat(mockWorld).isPresent();
    assertThat(mockWorld.get()).isInstanceOf(VerkleWorldState.class);
  }

  @Test
  public void shouldBePostTransition() {
    long now = System.currentTimeMillis();
    VerkleTransitionContext ctx = new VerkleTransitionContext(now);
    // assert we are AFTER transition timestamp
    assertThat(ctx.isBeforeTransition()).isFalse();
    assertThat(ctx.isBeforeTransition(now)).isFalse();

    // assert that a block built right now should be mutating using verkle state
    assertThat(
        ctx.isVerkleForMutation(
            BlockHeaderBuilder.createDefault().timestamp(now).buildBlockHeader()))
        .isTrue();

    var archive = getArchive(ctx);
    assertThat(archive).isNotNull();
    assertThat(archive.getMutable()).isNotNull();
    assertThat(archive.getMutable()).isInstanceOf(VerkleWorldState.class);

  }

  @Test
  public void shouldBeFinalized() {
    long now = System.currentTimeMillis();
    VerkleTransitionContext ctx = new VerkleTransitionContext(now);
    // assert we are AFTER transition timestamp
    assertThat(ctx.isBeforeTransition()).isFalse();
    assertThat(ctx.isBeforeTransition(now)).isFalse();

    // assert that a block built right now should be mutating using verkle state
    assertThat(
        ctx.isVerkleForMutation(
            BlockHeaderBuilder.createDefault().timestamp(now).buildBlockHeader()))
        .isTrue();

    // TODO: this is a placeholder tripwire test for now
    assertThat(ctx.isTransitionFinalized()).isFalse();
    assertThat(ctx.isTransitionFinalized(now)).isFalse();
  }

  private VerkleTransitionWorldStateProvider getArchive(final VerkleTransitionContext ctx) {
    var metrics = new NoOpMetricsSystem();
    var storageConfig = ImmutableDataStorageConfiguration.builder()
        .from(DataStorageConfiguration.DEFAULT_CONFIG)
        .dataStorageFormat(DataStorageFormat.VERKLE_TRANSITION)
        .verkleTransitionContext(ctx)
        .build();
    return new VerkleTransitionWorldStateProvider(
        blockchain,
        new BonsaiWorldStateProvider(
            new BonsaiWorldStateKeyValueStorage(storageProvider, metrics, storageConfig),
            blockchain, Optional.empty(), new BonsaiCachedMerkleTrieLoader(metrics),
            besuContext, EvmConfiguration.DEFAULT),
        new VerkleWorldStateProvider(
            new VerkleWorldStateKeyValueStorage(storageProvider, metrics),
            blockchain, Optional.empty(), besuContext, EvmConfiguration.DEFAULT),
        ctx);
  }
}
