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
import static tech.pegasys.pantheon.ethereum.core.InMemoryTestFixture.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryTestFixture.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueExtraData;
import tech.pegasys.pantheon.consensus.clique.CliqueHelpers;
import tech.pegasys.pantheon.consensus.clique.CliqueProtocolSchedule;
import tech.pegasys.pantheon.consensus.clique.CliqueProtocolSpecs;
import tech.pegasys.pantheon.consensus.clique.TestHelpers;
import tech.pegasys.pantheon.consensus.clique.VoteTallyCache;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.consensus.common.VoteType;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.GenesisConfig;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.PendingTransactions;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.mainnet.MutableProtocolSchedule;
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

  private final Block genesis = GenesisConfig.mainnet().getBlock();
  private final MutableBlockchain blockchain = createInMemoryBlockchain(genesis);
  private final WorldStateArchive stateArchive = createInMemoryWorldStateArchive();

  private ProtocolContext<CliqueContext> protocolContext;
  private final MutableProtocolSchedule<CliqueContext> protocolSchedule =
      new CliqueProtocolSchedule();
  private VoteProposer voteProposer;

  @Before
  public void setup() {
    final CliqueProtocolSpecs specs =
        new CliqueProtocolSpecs(
            15,
            30_000,
            1,
            Util.publicKeyToAddress(proposerKeyPair.getPublicKey()),
            protocolSchedule);

    protocolSchedule.putMilestone(0, specs.frontier());

    final Address otherAddress = Util.publicKeyToAddress(otherKeyPair.getPublicKey());
    validatorList.add(otherAddress);

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));
    voteProposer = new VoteProposer();
    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer, null);

    protocolContext = new ProtocolContext<>(blockchain, stateArchive, cliqueContext);

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

    final CliqueExtraData extraData =
        new CliqueExtraData(BytesValue.wrap(new byte[32]), null, validatorList);

    final Address coinbase = AddressHelpers.ofValue(1);
    final CliqueBlockCreator blockCreator =
        new CliqueBlockCreator(
            coinbase,
            parent -> extraData.encode(),
            new PendingTransactions(5),
            protocolContext,
            protocolSchedule,
            gasLimit -> gasLimit,
            proposerKeyPair,
            Wei.ZERO,
            blockchain.getChainHeadHeader());

    final Block createdBlock = blockCreator.createBlock(5L);

    assertThat(CliqueHelpers.getProposerOfBlock(createdBlock.getHeader()))
        .isEqualTo(proposerAddress);
  }

  @Test
  public void insertsValidVoteIntoConstructedBlock() {
    final CliqueExtraData extraData =
        new CliqueExtraData(BytesValue.wrap(new byte[32]), null, validatorList);
    final Address a1 = Address.fromHexString("5");
    voteProposer.auth(a1);
    final Address coinbase = AddressHelpers.ofValue(1);

    final CliqueBlockCreator blockCreator =
        new CliqueBlockCreator(
            coinbase,
            parent -> extraData.encode(),
            new PendingTransactions(5),
            protocolContext,
            protocolSchedule,
            gasLimit -> gasLimit,
            proposerKeyPair,
            Wei.ZERO,
            blockchain.getChainHeadHeader());

    final Block createdBlock = blockCreator.createBlock(0L);
    assertThat(createdBlock.getHeader().getNonce()).isEqualTo(VoteType.ADD.getNonceValue());
    assertThat(createdBlock.getHeader().getCoinbase()).isEqualTo(a1);
  }

  @Test
  public void insertsNoVoteWhenAuthInValidators() {
    final CliqueExtraData extraData =
        new CliqueExtraData(BytesValue.wrap(new byte[32]), null, validatorList);
    final Address a1 = Util.publicKeyToAddress(otherKeyPair.getPublicKey());
    voteProposer.auth(a1);
    final Address coinbase = AddressHelpers.ofValue(1);

    final CliqueBlockCreator blockCreator =
        new CliqueBlockCreator(
            coinbase,
            parent -> extraData.encode(),
            new PendingTransactions(5),
            protocolContext,
            protocolSchedule,
            gasLimit -> gasLimit,
            proposerKeyPair,
            Wei.ZERO,
            blockchain.getChainHeadHeader());

    final Block createdBlock = blockCreator.createBlock(0L);
    assertThat(createdBlock.getHeader().getNonce()).isEqualTo(VoteType.DROP.getNonceValue());
    assertThat(createdBlock.getHeader().getCoinbase()).isEqualTo(Address.fromHexString("0"));
  }
}
