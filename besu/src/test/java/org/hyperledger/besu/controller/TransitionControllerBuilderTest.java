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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.consensus.clique.BlockHeaderValidationRulesetFactory;
import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.PostMergeContext;
import org.hyperledger.besu.consensus.merge.TransitionProtocolSchedule;
import org.hyperledger.besu.consensus.merge.blockcreation.TransitionCoordinator;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
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

  CliqueBesuControllerBuilder cliqueBuilder = new CliqueBesuControllerBuilder();
  BesuControllerBuilder powBuilder = new MainnetBesuControllerBuilder();
  MergeBesuControllerBuilder postMergeBuilder = new MergeBesuControllerBuilder();
  MiningParameters miningParameters;

  TransitionProtocolSchedule transitionProtocolSchedule;

  @Before
  public void setup() {
    transitionProtocolSchedule =
        spy(
            new TransitionProtocolSchedule(
                preMergeProtocolSchedule, postMergeProtocolSchedule, mergeContext));
    transitionProtocolSchedule.setProtocolContext(protocolContext);
    cliqueBuilder.nodeKey(NodeKeyUtils.generate());
    cliqueBuilder.genesisConfigFile(GenesisConfigFile.DEFAULT);
    powBuilder.genesisConfigFile(GenesisConfigFile.DEFAULT);
    postMergeBuilder.genesisConfigFile(GenesisConfigFile.DEFAULT);
    postMergeBuilder.storageProvider(storageProvider);
    when(protocolContext.getBlockchain()).thenReturn(mockBlockchain);
    when(transitionProtocolSchedule.getPostMergeSchedule()).thenReturn(postMergeProtocolSchedule);
    when(transitionProtocolSchedule.getPreMergeSchedule()).thenReturn(preMergeProtocolSchedule);
    when(protocolContext.getConsensusContext(CliqueContext.class))
        .thenReturn(mock(CliqueContext.class));
    when(protocolContext.getConsensusContext(PostMergeContext.class)).thenReturn(mergeContext);
    when(protocolContext.getConsensusContext(MergeContext.class)).thenReturn(mergeContext);
    miningParameters = ImmutableMiningParameters.builder().isMiningEnabled(false).build();
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
    miningParameters = ImmutableMiningParameters.builder().isMiningEnabled(true).build();
    var transCoordinator = buildTransitionCoordinator(powBuilder, postMergeBuilder);
    assertThat(transCoordinator.isMiningBeforeMerge()).isTrue();
  }

  @Test
  public void assertPreMergeScheduleForNotPostMerge() {
    var mockBlock = new BlockHeaderTestFixture().buildHeader();
    var preMergeProtocolSpec = mock(ProtocolSpec.class);
    when(mergeContext.isPostMerge()).thenReturn(Boolean.FALSE);
    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(preMergeProtocolSchedule.getByBlockHeader(any())).thenReturn(preMergeProtocolSpec);
    assertThat(transitionProtocolSchedule.getByBlockHeaderWithTransitionReorgHandling(mockBlock))
        .isEqualTo(preMergeProtocolSpec);
  }

  @Test
  public void assertPostMergeScheduleForAnyBlockWhenPostMergeAndFinalized() {
    var mockBlock = new BlockHeaderTestFixture().buildHeader();
    var postMergeProtocolSpec = mock(ProtocolSpec.class);
    when(mergeContext.getFinalized()).thenReturn(Optional.of(mockBlock));
    when(postMergeProtocolSchedule.getByBlockHeader(any())).thenReturn(postMergeProtocolSpec);
    assertThat(transitionProtocolSchedule.getByBlockHeaderWithTransitionReorgHandling(mockBlock))
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

    when(preMergeProtocolSchedule.getByBlockHeader(any())).thenReturn(preMergeProtocolSpec);
    assertThat(transitionProtocolSchedule.getByBlockHeaderWithTransitionReorgHandling(mockBlock))
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

    when(postMergeProtocolSchedule.getByBlockHeader(any())).thenReturn(postMergeProtocolSpec);
    assertThat(transitionProtocolSchedule.getByBlockHeaderWithTransitionReorgHandling(mockBlock))
        .isEqualTo(postMergeProtocolSpec);
  }

  @Test
  public void assertCliqueDetachedHeaderValidationPreMerge() {
    BlockHeaderValidator cliqueValidator =
        BlockHeaderValidationRulesetFactory.cliqueBlockHeaderValidator(
                5L, new EpochManager(5L), Optional.of(FeeMarket.london(1L)), true)
            .build();
    assertDetachedRulesForPostMergeBlocks(cliqueValidator);
  }

  @Test
  public void assertPoWDetachedHeaderValidationPreMerge() {
    BlockHeaderValidator powValidator =
        MainnetBlockHeaderValidator.createBaseFeeMarketValidator(FeeMarket.london(1L), true)
            .build();
    assertDetachedRulesForPostMergeBlocks(powValidator);
  }

  void assertDetachedRulesForPostMergeBlocks(final BlockHeaderValidator validator) {
    var preMergeProtocolSpec = mock(ProtocolSpec.class);
    when(preMergeProtocolSpec.getBlockHeaderValidator()).thenReturn(validator);

    when(preMergeProtocolSchedule.getByBlockHeader(any())).thenReturn(preMergeProtocolSpec);

    var mockParentBlock =
        new BlockHeaderTestFixture()
            .number(100L)
            .baseFeePerGas(Wei.of(7L))
            .gasLimit(5000L)
            .buildHeader();

    var mockBlock =
        new BlockHeaderTestFixture()
            .number(101L)
            .timestamp(1000L)
            .baseFeePerGas(Wei.of(7L))
            .gasLimit(5000L)
            .difficulty(Difficulty.of(0L))
            .parentHash(mockParentBlock.getHash())
            .buildHeader();

    var mergeFriendlyValidation =
        transitionProtocolSchedule
            .getPreMergeSchedule()
            .getByBlockHeader(mockBlock)
            .getBlockHeaderValidator()
            .validateHeader(
                mockBlock, mockParentBlock, protocolContext, HeaderValidationMode.DETACHED_ONLY);
    assertThat(mergeFriendlyValidation).isTrue();
  }

  TransitionCoordinator buildTransitionCoordinator(
      final BesuControllerBuilder preMerge, final MergeBesuControllerBuilder postMerge) {
    var builder = new TransitionBesuControllerBuilder(preMerge, postMerge);
    builder.genesisConfigFile(GenesisConfigFile.mainnet());
    builder.storageProvider(storageProvider);
    builder.metricsSystem(new NoOpMetricsSystem());
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
