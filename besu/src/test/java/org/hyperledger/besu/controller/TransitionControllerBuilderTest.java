/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.PostMergeContext;
import org.hyperledger.besu.consensus.merge.TransitionProtocolSchedule;
import org.hyperledger.besu.consensus.merge.blockcreation.TransitionCoordinator;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.storage.StorageProvider;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * We only bother testing transitionControllerBuilder for PoW and Clique since those are the only
 * network types that are transitioning to PoS.
 */
@RunWith(MockitoJUnitRunner.class)
public class TransitionControllerBuilderTest {

  @Mock ProtocolSchedule preMergeProtocolSchedule;
  @Mock ProtocolSchedule postMergeProtocolSchedule;
  @Mock ProtocolContext protocolContext;
  @Mock MutableBlockchain mockBlockchain;
  @Mock TransactionPool transactionPool;
  @Mock SyncState syncState;
  @Mock EthProtocolManager ethProtocolManager;
  @Mock PostMergeContext mergeContext;
  StorageProvider storageProvider = new InMemoryKeyValueStorageProvider();

  @Spy CliqueBesuControllerBuilder cliqueBuilder = new CliqueBesuControllerBuilder();
  @Spy BesuControllerBuilder powBuilder = new MainnetBesuControllerBuilder();
  @Spy MergeBesuControllerBuilder postMergeBuilder = new MergeBesuControllerBuilder();
  @Spy MiningParameters miningParameters = new MiningParameters.Builder().build();

  TransitionProtocolSchedule transitionProtocolSchedule;

  @Before
  public void setup() {
    transitionProtocolSchedule =
        spy(
            new TransitionProtocolSchedule(
                preMergeProtocolSchedule, postMergeProtocolSchedule, mergeContext));
    cliqueBuilder.nodeKey(NodeKeyUtils.generate());
    postMergeBuilder.storageProvider(storageProvider);
    when(protocolContext.getBlockchain()).thenReturn(mockBlockchain);
    when(transitionProtocolSchedule.getPostMergeSchedule()).thenReturn(postMergeProtocolSchedule);
    when(transitionProtocolSchedule.getPreMergeSchedule()).thenReturn(preMergeProtocolSchedule);
    when(protocolContext.getConsensusContext(CliqueContext.class))
        .thenReturn(mock(CliqueContext.class));
    when(protocolContext.getConsensusContext(PostMergeContext.class)).thenReturn(mergeContext);
    when(protocolContext.getConsensusContext(MergeContext.class)).thenReturn(mergeContext);
  }

  @Test
  public void assertCliqueMiningOverridePreMerge() {
    assertThat(miningParameters.isMiningEnabled()).isFalse();
    var transCoordinator = buildTransitionCoordinator(cliqueBuilder, postMergeBuilder);
    assertThat(transCoordinator.isMiningBeforeMerge()).isTrue();
  }

  @Test
  public void assertPoWIsNotMiningPreMerge() {
    assertThat(miningParameters.isMiningEnabled()).isFalse();
    var transCoordinator = buildTransitionCoordinator(powBuilder, postMergeBuilder);
    assertThat(transCoordinator.isMiningBeforeMerge()).isFalse();
  }

  @Test
  public void assertPowMiningPreMerge() {
    when(miningParameters.isMiningEnabled()).thenReturn(Boolean.TRUE);
    var transCoordinator = buildTransitionCoordinator(powBuilder, postMergeBuilder);
    assertThat(transCoordinator.isMiningBeforeMerge()).isTrue();
  }

  @Test
  public void assertPreMergeScheduleForNotPostMerge() {
    var mockBlock = new BlockHeaderTestFixture().buildHeader();
    var preMergeProtocolSpec = mock(ProtocolSpec.class);
    when(mergeContext.isPostMerge()).thenReturn(Boolean.FALSE);
    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(preMergeProtocolSchedule.getByBlockNumber(anyLong())).thenReturn(preMergeProtocolSpec);
    assertThat(transitionProtocolSchedule.getByBlockHeader(protocolContext, mockBlock))
        .isEqualTo(preMergeProtocolSpec);
  }

  @Test
  public void assertPostMergeScheduleForAnyBlockWhenPostMergeAndFinalized() {
    var mockBlock = new BlockHeaderTestFixture().buildHeader();
    var postMergeProtocolSpec = mock(ProtocolSpec.class);
    when(mergeContext.getFinalized()).thenReturn(Optional.of(mockBlock));
    when(postMergeProtocolSchedule.getByBlockNumber(anyLong())).thenReturn(postMergeProtocolSpec);
    assertThat(transitionProtocolSchedule.getByBlockHeader(protocolContext, mockBlock))
        .isEqualTo(postMergeProtocolSpec);
  }

  @Test
  public void assertPreMergeScheduleForBelowTerminalBlockWhenPostMergeIfNotFinalized() {
    var mockParentBlock = new BlockHeaderTestFixture().number(100).buildHeader();
    var mockBlock =
        new BlockHeaderTestFixture()
            .number(101)
            .difficulty(Difficulty.of(1L))
            .parentHash(mockParentBlock.getHash())
            .buildHeader();

    var preMergeProtocolSpec = mock(ProtocolSpec.class);

    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(Difficulty.of(1337L));
    when(mergeContext.isPostMerge()).thenReturn(Boolean.TRUE);
    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(mockBlockchain.getTotalDifficultyByHash(any()))
        .thenReturn(Optional.of(Difficulty.of(1335L)));

    when(preMergeProtocolSchedule.getByBlockNumber(anyLong())).thenReturn(preMergeProtocolSpec);
    assertThat(transitionProtocolSchedule.getByBlockHeader(protocolContext, mockBlock))
        .isEqualTo(preMergeProtocolSpec);
  }

  @Test
  public void assertPostMergeScheduleForPostMergeExactlyAtTerminalDifficultyIfNotFinalized() {
    var mockParentBlock = new BlockHeaderTestFixture().number(100).buildHeader();
    var mockBlock =
        new BlockHeaderTestFixture()
            .number(101)
            .difficulty(Difficulty.of(0L))
            .parentHash(mockParentBlock.getHash())
            .buildHeader();

    var postMergeProtocolSpec = mock(ProtocolSpec.class);

    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(Difficulty.of(1337L));
    when(mergeContext.isPostMerge()).thenReturn(Boolean.TRUE);
    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(mockBlockchain.getTotalDifficultyByHash(any()))
        .thenReturn(Optional.of(Difficulty.of(1337L)));

    when(postMergeProtocolSchedule.getByBlockNumber(anyLong())).thenReturn(postMergeProtocolSpec);
    assertThat(transitionProtocolSchedule.getByBlockHeader(protocolContext, mockBlock))
        .isEqualTo(postMergeProtocolSpec);
  }

  TransitionCoordinator buildTransitionCoordinator(
      final BesuControllerBuilder preMerge, final MergeBesuControllerBuilder postMerge) {
    var builder = new TransitionBesuControllerBuilder(preMerge, postMerge);
    builder.storageProvider(storageProvider);
    var coordinator =
        builder.createMiningCoordinator(
            transitionProtocolSchedule,
            protocolContext,
            transactionPool,
            miningParameters,
            syncState,
            ethProtocolManager);

    assertThat(coordinator).isInstanceOf(TransitionCoordinator.class);
    var transCoordinator = (TransitionCoordinator) coordinator;
    return transCoordinator;
  }
}
