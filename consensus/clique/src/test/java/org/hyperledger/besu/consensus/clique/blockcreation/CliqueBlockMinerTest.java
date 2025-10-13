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
package org.hyperledger.besu.consensus.clique.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.CliqueConfigOptions;
import org.hyperledger.besu.config.ImmutableCliqueConfigOptions;
import org.hyperledger.besu.config.JsonCliqueConfigOptions;
import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.common.ForkSpec;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreationTiming;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.blockcreation.DefaultBlockScheduler;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult;
import org.hyperledger.besu.ethereum.mainnet.DefaultProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.util.Subscribers;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CliqueBlockMinerTest {

  private ForksSchedule<CliqueConfigOptions> forksSchedule;

  @BeforeEach
  public void setup() {
    var options = ImmutableCliqueConfigOptions.builder().from(JsonCliqueConfigOptions.DEFAULT);
    options.createEmptyBlocks(false);
    forksSchedule = new ForksSchedule<>(List.of(new ForkSpec<>(0, options.build())));
  }

  @Test
  void doesNotMineBlockIfNoTransactionsWhenEmptyBlocksNotAllowed() throws InterruptedException {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    final Block blockToCreate =
        new Block(
            headerBuilder.buildHeader(), new BlockBody(Lists.newArrayList(), Lists.newArrayList()));

    final ValidatorProvider validatorProvider = mock(ValidatorProvider.class);
    when(validatorProvider.getValidatorsAfterBlock(any())).thenReturn(List.of(Address.ZERO));

    final CliqueContext cliqueContext = new CliqueContext(validatorProvider, null, null);
    final ProtocolContext protocolContext =
        new ProtocolContext(null, null, cliqueContext, new BadBlockManager());

    final CliqueBlockCreator blockCreator = mock(CliqueBlockCreator.class);
    final Function<BlockHeader, CliqueBlockCreator> blockCreatorSupplier =
        (parentHeader) -> blockCreator;
    when(blockCreator.createBlock(anyLong(), any()))
        .thenReturn(
            new BlockCreator.BlockCreationResult(
                blockToCreate, new TransactionSelectionResults(), new BlockCreationTiming()));

    final BlockImporter blockImporter = mock(BlockImporter.class);
    final ProtocolSpec protocolSpec = mock(ProtocolSpec.class);

    final ProtocolSchedule protocolSchedule = singleSpecSchedule(protocolSpec);

    when(protocolSpec.getBlockImporter()).thenReturn(blockImporter);
    when(blockImporter.importBlock(any(), any(), any())).thenReturn(new BlockImportResult(true));

    final MinedBlockObserver observer = mock(MinedBlockObserver.class);
    final DefaultBlockScheduler scheduler = mock(DefaultBlockScheduler.class);
    when(scheduler.waitUntilNextBlockCanBeMined(any())).thenReturn(5L);
    final CliqueBlockMiner miner =
        new CliqueBlockMiner(
            blockCreatorSupplier,
            protocolSchedule,
            protocolContext,
            subscribersContaining(observer),
            scheduler,
            headerBuilder.buildHeader(),
            Address.ZERO,
            forksSchedule); // parent header is arbitrary for the test.

    final boolean result = miner.mineBlock();
    assertThat(result).isFalse();
    verify(blockImporter, never())
        .importBlock(protocolContext, blockToCreate, HeaderValidationMode.FULL);
    verify(observer, never()).blockMined(blockToCreate);
  }

  @Test
  void minesBlockIfHasTransactionsWhenEmptyBlocksNotAllowed() throws InterruptedException {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    final TransactionTestFixture transactionTestFixture = new TransactionTestFixture();
    final KeyPair keyPair = SignatureAlgorithmFactory.getInstance().generateKeyPair();
    final Transaction transaction = transactionTestFixture.createTransaction(keyPair);

    final Block blockToCreate =
        new Block(
            headerBuilder.buildHeader(), new BlockBody(List.of(transaction), Lists.newArrayList()));

    final ValidatorProvider validatorProvider = mock(ValidatorProvider.class);
    when(validatorProvider.getValidatorsAfterBlock(any())).thenReturn(List.of(Address.ZERO));

    final CliqueContext cliqueContext = new CliqueContext(validatorProvider, null, null);
    final ProtocolContext protocolContext =
        new ProtocolContext(null, null, cliqueContext, new BadBlockManager());

    final CliqueBlockCreator blockCreator = mock(CliqueBlockCreator.class);
    final Function<BlockHeader, CliqueBlockCreator> blockCreatorSupplier =
        (parentHeader) -> blockCreator;
    when(blockCreator.createBlock(anyLong(), any()))
        .thenReturn(
            new BlockCreator.BlockCreationResult(
                blockToCreate, new TransactionSelectionResults(), new BlockCreationTiming()));

    final BlockImporter blockImporter = mock(BlockImporter.class);
    final ProtocolSpec protocolSpec = mock(ProtocolSpec.class);

    final ProtocolSchedule protocolSchedule = singleSpecSchedule(protocolSpec);

    when(protocolSpec.getBlockImporter()).thenReturn(blockImporter);
    when(blockImporter.importBlock(any(), any(), any())).thenReturn(new BlockImportResult(true));

    final MinedBlockObserver observer = mock(MinedBlockObserver.class);
    final DefaultBlockScheduler scheduler = mock(DefaultBlockScheduler.class);
    when(scheduler.waitUntilNextBlockCanBeMined(any())).thenReturn(5L);
    final CliqueBlockMiner miner =
        new CliqueBlockMiner(
            blockCreatorSupplier,
            protocolSchedule,
            protocolContext,
            subscribersContaining(observer),
            scheduler,
            headerBuilder.buildHeader(),
            Address.ZERO,
            forksSchedule); // parent header is arbitrary for the test.

    final boolean result = miner.mineBlock();
    assertThat(result).isTrue();
    verify(blockImporter).importBlock(protocolContext, blockToCreate, HeaderValidationMode.FULL);
    verify(observer).blockMined(blockToCreate);
  }

  private static Subscribers<MinedBlockObserver> subscribersContaining(
      final MinedBlockObserver... observers) {
    final Subscribers<MinedBlockObserver> result = Subscribers.create();
    for (final MinedBlockObserver obs : observers) {
      result.subscribe(obs);
    }
    return result;
  }

  private ProtocolSchedule singleSpecSchedule(final ProtocolSpec protocolSpec) {
    final DefaultProtocolSchedule protocolSchedule =
        new DefaultProtocolSchedule(Optional.of(BigInteger.valueOf(1234)));
    protocolSchedule.putBlockNumberMilestone(0, protocolSpec);
    return protocolSchedule;
  }
}
