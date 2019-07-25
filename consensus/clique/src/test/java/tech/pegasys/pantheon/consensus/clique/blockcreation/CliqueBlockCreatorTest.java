/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.clique.blockcreation;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.consensus.clique.CliqueBlockInterface;
import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueExtraData;
import tech.pegasys.pantheon.consensus.clique.CliqueHelpers;
import tech.pegasys.pantheon.consensus.clique.CliqueProtocolSchedule;
import tech.pegasys.pantheon.consensus.clique.TestHelpers;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.consensus.common.VoteTallyCache;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class CliqueBlockCreatorTest {

  private final KeyPair proposerKeyPair = KeyPair.generate();
  private final Address proposerAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());
  private final KeyPair otherKeyPair = KeyPair.generate();
  private final List<Address> validatorList = Lists.newArrayList();
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

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
            GenesisConfigFile.DEFAULT.getConfigOptions(),
            proposerKeyPair,
            false,
            TestClock.fixed());

    final Address otherAddress = Util.publicKeyToAddress(otherKeyPair.getPublicKey());
    validatorList.add(otherAddress);

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAfterBlock(any())).thenReturn(new VoteTally(validatorList));
    voteProposer = new VoteProposer();
    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer, null);

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

    final BytesValue extraData =
        CliqueExtraData.createWithoutProposerSeal(BytesValue.wrap(new byte[32]), validatorList);

    final Address coinbase = AddressHelpers.ofValue(1);
    final CliqueBlockCreator blockCreator =
        new CliqueBlockCreator(
            coinbase,
            parent -> extraData,
            new PendingTransactions(
                TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
                5,
                TestClock.fixed(),
                metricsSystem),
            protocolContext,
            protocolSchedule,
            gasLimit -> gasLimit,
            proposerKeyPair,
            Wei.ZERO,
            blockchain.getChainHeadHeader(),
            epochManager);

    final Block createdBlock = blockCreator.createBlock(5L);

    assertThat(CliqueHelpers.getProposerOfBlock(createdBlock.getHeader()))
        .isEqualTo(proposerAddress);
  }

  @Test
  public void insertsValidVoteIntoConstructedBlock() {
    final BytesValue extraData =
        CliqueExtraData.createWithoutProposerSeal(BytesValue.wrap(new byte[32]), validatorList);
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
                TestClock.fixed(),
                metricsSystem),
            protocolContext,
            protocolSchedule,
            gasLimit -> gasLimit,
            proposerKeyPair,
            Wei.ZERO,
            blockchain.getChainHeadHeader(),
            epochManager);

    final Block createdBlock = blockCreator.createBlock(0L);
    assertThat(createdBlock.getHeader().getNonce()).isEqualTo(CliqueBlockInterface.ADD_NONCE);
    assertThat(createdBlock.getHeader().getCoinbase()).isEqualTo(a1);
  }

  @Test
  public void insertsNoVoteWhenAuthInValidators() {
    final BytesValue extraData =
        CliqueExtraData.createWithoutProposerSeal(BytesValue.wrap(new byte[32]), validatorList);
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
                TestClock.fixed(),
                metricsSystem),
            protocolContext,
            protocolSchedule,
            gasLimit -> gasLimit,
            proposerKeyPair,
            Wei.ZERO,
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

    final BytesValue extraData =
        CliqueExtraData.createWithoutProposerSeal(BytesValue.wrap(new byte[32]), validatorList);
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
                TestClock.fixed(),
                metricsSystem),
            protocolContext,
            protocolSchedule,
            gasLimit -> gasLimit,
            proposerKeyPair,
            Wei.ZERO,
            blockchain.getChainHeadHeader(),
            epochManager);

    final Block createdBlock = blockCreator.createBlock(0L);
    assertThat(createdBlock.getHeader().getNonce()).isEqualTo(CliqueBlockInterface.DROP_NONCE);
    assertThat(createdBlock.getHeader().getCoinbase()).isEqualTo(Address.fromHexString("0"));
  }
}
