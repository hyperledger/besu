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
package org.hyperledger.besu.consensus.clique.blockcreation;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.consensus.clique.CliqueBlockInterface;
import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.clique.CliqueExtraData;
import org.hyperledger.besu.consensus.clique.CliqueHelpers;
import org.hyperledger.besu.consensus.clique.CliqueProtocolSchedule;
import org.hyperledger.besu.consensus.clique.TestHelpers;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.consensus.common.validator.VoteType;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Java6Assertions;
import org.junit.Before;
import org.junit.Test;

public class CliqueBlockCreatorTest {

  private final NodeKey proposerNodeKey = NodeKeyUtils.generate();
  private final Address proposerAddress = Util.publicKeyToAddress(proposerNodeKey.getPublicKey());
  private final KeyPair otherKeyPair = SignatureAlgorithmFactory.getInstance().generateKeyPair();
  private final List<Address> validatorList = Lists.newArrayList();
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final CliqueBlockInterface blockInterface = new CliqueBlockInterface();

  private ProtocolSchedule protocolSchedule;
  private final WorldStateArchive stateArchive = createInMemoryWorldStateArchive();

  private MutableBlockchain blockchain;
  private ProtocolContext protocolContext;
  private EpochManager epochManager;
  private ValidatorProvider validatorProvider;
  private VoteProvider voteProvider;

  @Before
  public void setup() {
    protocolSchedule =
        CliqueProtocolSchedule.create(
            GenesisConfigFile.DEFAULT.getConfigOptions(),
            proposerNodeKey,
            false,
            EvmConfiguration.DEFAULT);

    final Address otherAddress = Util.publicKeyToAddress(otherKeyPair.getPublicKey());
    validatorList.add(otherAddress);

    validatorProvider = mock(ValidatorProvider.class);
    voteProvider = mock(VoteProvider.class);
    when(validatorProvider.getVoteProviderAtHead()).thenReturn(Optional.of(voteProvider));
    when(validatorProvider.getValidatorsAfterBlock(any())).thenReturn(validatorList);
    final CliqueContext cliqueContext = new CliqueContext(validatorProvider, null, blockInterface);

    final Block genesis =
        GenesisState.fromConfig(GenesisConfigFile.mainnet(), protocolSchedule).getBlock();
    blockchain = createInMemoryBlockchain(genesis);
    protocolContext = new ProtocolContext(blockchain, stateArchive, cliqueContext);
    epochManager = new EpochManager(10);

    // Add a block above the genesis
    final BlockHeaderTestFixture headerTestFixture = new BlockHeaderTestFixture();
    headerTestFixture.number(1).parentHash(genesis.getHeader().getHash());
    final Block emptyBlock =
        new Block(
            TestHelpers.createCliqueSignedBlockHeader(
                headerTestFixture, otherKeyPair, validatorList),
            new BlockBody(Lists.newArrayList(), Lists.newArrayList()));
    blockchain.appendBlock(emptyBlock, Lists.newArrayList());
  }

  @Test
  public void proposerAddressCanBeExtractFromAConstructedBlock() {

    final Bytes extraData =
        CliqueExtraData.createWithoutProposerSeal(Bytes.wrap(new byte[32]), validatorList);

    final Address coinbase = AddressHelpers.ofValue(1);
    final CliqueBlockCreator blockCreator =
        new CliqueBlockCreator(
            coinbase,
            () -> Optional.of(10_000_000L),
            parent -> extraData,
            new GasPricePendingTransactionsSorter(
                TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
                5,
                TestClock.fixed(),
                metricsSystem,
                blockchain::getChainHeadHeader,
                TransactionPoolConfiguration.DEFAULT_PRICE_BUMP),
            protocolContext,
            protocolSchedule,
            proposerNodeKey,
            Wei.ZERO,
            0.8,
            blockchain.getChainHeadHeader(),
            epochManager);

    final Block createdBlock = blockCreator.createBlock(5L);

    Java6Assertions.assertThat(CliqueHelpers.getProposerOfBlock(createdBlock.getHeader()))
        .isEqualTo(proposerAddress);
  }

  @Test
  public void insertsValidVoteIntoConstructedBlock() {
    final Bytes extraData =
        CliqueExtraData.createWithoutProposerSeal(Bytes.wrap(new byte[32]), validatorList);
    final Address a1 = Address.fromHexString("5");
    final Address coinbase = AddressHelpers.ofValue(1);
    when(voteProvider.getVoteAfterBlock(any(), any()))
        .thenReturn(Optional.of(new ValidatorVote(VoteType.ADD, coinbase, a1)));

    final CliqueBlockCreator blockCreator =
        new CliqueBlockCreator(
            coinbase,
            () -> Optional.of(10_000_000L),
            parent -> extraData,
            new GasPricePendingTransactionsSorter(
                TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
                5,
                TestClock.fixed(),
                metricsSystem,
                blockchain::getChainHeadHeader,
                TransactionPoolConfiguration.DEFAULT_PRICE_BUMP),
            protocolContext,
            protocolSchedule,
            proposerNodeKey,
            Wei.ZERO,
            0.8,
            blockchain.getChainHeadHeader(),
            epochManager);

    final Block createdBlock = blockCreator.createBlock(0L);
    assertThat(createdBlock.getHeader().getNonce()).isEqualTo(CliqueBlockInterface.ADD_NONCE);
    assertThat(createdBlock.getHeader().getCoinbase()).isEqualTo(a1);
  }

  @Test
  public void insertsNoVoteWhenAtEpoch() {
    // ensure that the next block is epoch
    epochManager = new EpochManager(1);

    final Bytes extraData =
        CliqueExtraData.createWithoutProposerSeal(Bytes.wrap(new byte[32]), validatorList);
    final Address a1 = Address.fromHexString("5");
    final Address coinbase = AddressHelpers.ofValue(1);
    when(validatorProvider.getVoteProviderAtHead().get().getVoteAfterBlock(any(), any()))
        .thenReturn(Optional.of(new ValidatorVote(VoteType.ADD, coinbase, a1)));

    final CliqueBlockCreator blockCreator =
        new CliqueBlockCreator(
            coinbase,
            () -> Optional.of(10_000_000L),
            parent -> extraData,
            new GasPricePendingTransactionsSorter(
                TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
                5,
                TestClock.fixed(),
                metricsSystem,
                blockchain::getChainHeadHeader,
                TransactionPoolConfiguration.DEFAULT_PRICE_BUMP),
            protocolContext,
            protocolSchedule,
            proposerNodeKey,
            Wei.ZERO,
            0.8,
            blockchain.getChainHeadHeader(),
            epochManager);

    final Block createdBlock = blockCreator.createBlock(0L);
    assertThat(createdBlock.getHeader().getNonce()).isEqualTo(CliqueBlockInterface.DROP_NONCE);
    assertThat(createdBlock.getHeader().getCoinbase()).isEqualTo(Address.fromHexString("0"));
  }
}
