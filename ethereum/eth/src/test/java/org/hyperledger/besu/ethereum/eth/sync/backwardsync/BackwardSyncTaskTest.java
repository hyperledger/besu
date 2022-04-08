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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BackwardSyncTaskTest {

  public static final int HEIGHT = 20_000;

  @Mock private BackwardSyncContext context;
  private List<Block> blocks;

  GenericKeyValueStorageFacade<Hash, BlockHeader> headersStorage;
  GenericKeyValueStorageFacade<Hash, Block> blocksStorage;

  @Before
  public void initContextAndChain() {
    blocks = ChainForTestCreator.prepareChain(2, HEIGHT);
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
  }

  @Test
  public void shouldFailWhenPivotNotSetInContext() {
    when(context.getCurrentChain()).thenReturn(Optional.empty());
    BackwardSyncTask step = createBackwardSyncTask();
    CompletableFuture<Void> completableFuture = step.executeAsync(null);
    assertThatThrownBy(completableFuture::get)
        .getCause()
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("No pivot");
  }

  @Nonnull
  private BackwardSyncTask createBackwardSyncTask() {
    final BackwardChain backwardChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(1));
    return createBackwardSyncTask(backwardChain);
  }

  @Nonnull
  private BackwardSyncTask createBackwardSyncTask(final BackwardChain backwardChain) {
    return new BackwardSyncTask(context, backwardChain) {
      @Override
      CompletableFuture<Void> executeStep() {
        return CompletableFuture.completedFuture(null);
      }
    };
  }

  @Test
  public void shouldFinishImmediatelyFailWhenPivotIsDifferent() {
    final BackwardChain backwardChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(0));
    when(context.getCurrentChain()).thenReturn(Optional.of(backwardChain));
    BackwardSyncTask step = createBackwardSyncTask();
    CompletableFuture<Void> completableFuture = step.executeAsync(null);
    assertThat(completableFuture.isDone()).isTrue();
  }

  @Test
  public void shouldExecuteWhenPivotIsCorrect() {
    final BackwardChain backwardChain =
        new BackwardChain(headersStorage, blocksStorage, blocks.get(1));
    BackwardSyncTask step = createBackwardSyncTask();
    when(context.getCurrentChain()).thenReturn(Optional.of(backwardChain));
    CompletableFuture<Void> completableFuture = step.executeAsync(null);
    assertThat(completableFuture.isDone()).isTrue();
  }
}
