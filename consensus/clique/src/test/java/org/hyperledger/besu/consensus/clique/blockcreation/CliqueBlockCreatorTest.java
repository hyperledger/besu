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
import static org.hyperledger.besu.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;
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
import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.common.VoteTally;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.util.List;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Java6Assertions;
import org.junit.Before;
import org.junit.Test;

public class CliqueBlockCreatorTest {

  private final NodeKey proposerNodeKey = NodeKeyUtils.generate();
  private final Address proposerAddress = Util.publicKeyToAddress(proposerNodeKey.getPublicKey());
  private final KeyPair otherKeyPair = KeyPair.generate();
  private final List<Address> validatorList = Lists.newArrayList();
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final CliqueBlockInterface blockInterface = new CliqueBlockInterface();

  private ProtocolSchedule<CliqueContext> protocolSchedule;
  private final WorldStateArchive stateArchive = createInMemoryWorldStateArchive();

  private MutableBlockchain blockchain;
  private ProtocolContext<CliqueContext> protocolContext;
  private VoteProposer voteProposer;
  private EpochManager epochManager;

  @Before
  public void setup() {
    protocolSchedule =
        CliqueProtocolSchedule.create(
            GenesisConfigFile.DEFAULT.getConfigOptions(), proposerNodeKey, false);

    final Address otherAddress = Util.publicKeyToAddress(otherKeyPair.getPublicKey());
    validatorList.add(otherAddress);

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAfterBlock(any())).thenReturn(new VoteTally(validatorList));
    voteProposer = new VoteProposer();
    final CliqueContext cliqueContext =
        new CliqueContext(voteTallyCache, voteProposer, null, blockInterface);

    final Block genesis =
        GenesisState.fromConfig(GenesisConfigFile.mainnet(), protocolSchedule).getBlock();
    blockchain = createInMemoryBlockchain(genesis);
    protocolContext = new ProtocolContext<>(blockchain, stateArchive, cliqueContext);
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
            parent -> extraData,
            new PendingTransactions(
                TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
                5,
                5,
                TestClock.fixed(),
                metricsSystem),
            protocolContext,
            protocolSchedule,
            gasLimit -> gasLimit,
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
    voteProposer.auth(a1);
    final Address coinbase = AddressHelpers.ofValue(1);

    final CliqueBlockCreator blockCreator =
        new CliqueBlockCreator(
            coinbase,
            parent -> extraData,
            new PendingTransactions(
                TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
                5,
                5,
                TestClock.fixed(),
                metricsSystem),
            protocolContext,
            protocolSchedule,
            gasLimit -> gasLimit,
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
  public void insertsNoVoteWhenAuthInValidators() {
    final Bytes extraData =
        CliqueExtraData.createWithoutProposerSeal(Bytes.wrap(new byte[32]), validatorList);
    final Address a1 = Util.publicKeyToAddress(otherKeyPair.getPublicKey());
    voteProposer.auth(a1);
    final Address coinbase = AddressHelpers.ofValue(1);

    final CliqueBlockCreator blockCreator =
        new CliqueBlockCreator(
            coinbase,
            parent -> extraData,
            new PendingTransactions(
                TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
                5,
                5,
                TestClock.fixed(),
                metricsSystem),
            protocolContext,
            protocolSchedule,
            gasLimit -> gasLimit,
            proposerNodeKey,
            Wei.ZERO,
            0.8,
            blockchain.getChainHeadHeader(),
            epochManager);

    final Block createdBlock = blockCreator.createBlock(0L);
    assertThat(createdBlock.getHeader().getNonce()).isEqualTo(CliqueBlockInterface.DROP_NONCE);
    assertThat(createdBlock.getHeader().getCoinbase()).isEqualTo(Address.fromHexString("0"));
  }

  @Test
  public void insertsNoVoteWhenAtEpoch() {
    // ensure that the next block is epoch
    epochManager = new EpochManager(1);

    final Bytes extraData =
        CliqueExtraData.createWithoutProposerSeal(Bytes.wrap(new byte[32]), validatorList);
    final Address a1 = Address.fromHexString("5");
    voteProposer.auth(a1);
    final Address coinbase = AddressHelpers.ofValue(1);

    final CliqueBlockCreator blockCreator =
        new CliqueBlockCreator(
            coinbase,
            parent -> extraData,
            new PendingTransactions(
                TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
                5,
                5,
                TestClock.fixed(),
                metricsSystem),
            protocolContext,
            protocolSchedule,
            gasLimit -> gasLimit,
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
