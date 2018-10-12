package net.consensys.pantheon.consensus.clique.blockcreation;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.consensus.clique.CliqueContext;
import net.consensys.pantheon.consensus.clique.CliqueExtraData;
import net.consensys.pantheon.consensus.clique.CliqueHelpers;
import net.consensys.pantheon.consensus.clique.CliqueProtocolSchedule;
import net.consensys.pantheon.consensus.clique.CliqueProtocolSpecs;
import net.consensys.pantheon.consensus.clique.TestHelpers;
import net.consensys.pantheon.consensus.clique.VoteTallyCache;
import net.consensys.pantheon.consensus.common.VoteProposer;
import net.consensys.pantheon.consensus.common.VoteTally;
import net.consensys.pantheon.consensus.common.VoteType;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.chain.MutableBlockchain;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.AddressHelpers;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.core.Util;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.ethereum.mainnet.MutableProtocolSchedule;
import net.consensys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.services.kvstore.KeyValueStorage;
import net.consensys.pantheon.util.bytes.BytesValue;

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
  private final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
  private final MutableBlockchain blockchain =
      new DefaultMutableBlockchain(genesis, keyValueStorage, MainnetBlockHashFunction::createHash);
  private final WorldStateArchive stateArchive =
      new WorldStateArchive(new KeyValueStorageWorldStateStorage(keyValueStorage));

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
    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer);

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
