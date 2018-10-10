package net.consensys.pantheon.consensus.clique;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.consensus.clique.headervalidationrules.SignerRateLimitValidationRule;
import net.consensys.pantheon.consensus.common.VoteProposer;
import net.consensys.pantheon.consensus.common.VoteTally;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.AddressHelpers;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.Util;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.services.kvstore.KeyValueStorage;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class NodeCanProduceNextBlockTest {

  private final KeyPair proposerKeyPair = KeyPair.generate();
  private Address localAddress;
  private final KeyPair otherNodeKeyPair = KeyPair.generate();
  private final List<Address> validatorList = Lists.newArrayList();
  private final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
  private ProtocolContext<CliqueContext> cliqueProtocolContext;

  DefaultMutableBlockchain blockChain;
  private Block genesisBlock;

  private Block createEmptyBlock(final KeyPair blockSigner) {
    final BlockHeader header =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, blockSigner, validatorList);
    return new Block(header, new BlockBody(Lists.newArrayList(), Lists.newArrayList()));
  }

  @Before
  public void setup() {
    localAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());
    validatorList.add(localAddress);
  }

  @Test
  public void networkWithOneValidatorIsAllowedToCreateConsecutiveBlocks() {
    final Address localAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    genesisBlock = createEmptyBlock(proposerKeyPair);

    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    blockChain =
        new DefaultMutableBlockchain(
            genesisBlock, keyValueStorage, MainnetBlockHashFunction::createHash);

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));
    final VoteProposer voteProposer = new VoteProposer();
    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer);
    cliqueProtocolContext = new ProtocolContext<>(blockChain, null, cliqueContext);

    headerBuilder.number(1).parentHash(genesisBlock.getHash());
    final Block block_1 = createEmptyBlock(proposerKeyPair);
    blockChain.appendBlock(block_1, Lists.newArrayList());

    assertThat(
            CliqueHelpers.addressIsAllowedToProduceNextBlock(
                localAddress, cliqueProtocolContext, block_1.getHeader()))
        .isTrue();
  }

  @Test
  public void networkWithTwoValidatorsIsAllowedToProduceBlockIfNotPreviousBlockProposer() {
    final Address otherAddress = Util.publicKeyToAddress(otherNodeKeyPair.getPublicKey());
    validatorList.add(otherAddress);

    genesisBlock = createEmptyBlock(otherNodeKeyPair);

    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    blockChain =
        new DefaultMutableBlockchain(
            genesisBlock, keyValueStorage, MainnetBlockHashFunction::createHash);

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));
    final VoteProposer voteProposer = new VoteProposer();
    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer);
    cliqueProtocolContext = new ProtocolContext<>(blockChain, null, cliqueContext);

    headerBuilder.number(1).parentHash(genesisBlock.getHash());
    final Block block_1 = createEmptyBlock(proposerKeyPair);
    blockChain.appendBlock(block_1, Lists.newArrayList());

    headerBuilder.number(2).parentHash(block_1.getHash());
    final Block block_2 = createEmptyBlock(otherNodeKeyPair);
    blockChain.appendBlock(block_2, Lists.newArrayList());

    assertThat(
            CliqueHelpers.addressIsAllowedToProduceNextBlock(
                localAddress, cliqueProtocolContext, block_1.getHeader()))
        .isFalse();

    assertThat(
            CliqueHelpers.addressIsAllowedToProduceNextBlock(
                localAddress, cliqueProtocolContext, block_2.getHeader()))
        .isTrue();
  }

  @Test
  public void networkWithTwoValidatorsIsNotAllowedToProduceBlockIfIsPreviousBlockProposer() {
    final Address otherAddress = Util.publicKeyToAddress(otherNodeKeyPair.getPublicKey());
    validatorList.add(otherAddress);

    genesisBlock = createEmptyBlock(proposerKeyPair);

    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    blockChain =
        new DefaultMutableBlockchain(
            genesisBlock, keyValueStorage, MainnetBlockHashFunction::createHash);

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));
    final VoteProposer voteProposer = new VoteProposer();
    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer);
    cliqueProtocolContext = new ProtocolContext<>(blockChain, null, cliqueContext);

    headerBuilder.parentHash(genesisBlock.getHash()).number(1);
    final Block block_1 = createEmptyBlock(proposerKeyPair);
    blockChain.appendBlock(block_1, Lists.newArrayList());

    headerBuilder.parentHash(block_1.getHeader().getHash()).number(2);
    final BlockHeader block_2 =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, proposerKeyPair, validatorList);

    final SignerRateLimitValidationRule validationRule = new SignerRateLimitValidationRule();

    assertThat(validationRule.validate(block_2, block_1.getHeader(), cliqueProtocolContext))
        .isFalse();
  }

  @Test
  public void withThreeValidatorsMustHaveOneBlockBetweenSignings() {
    final Address otherAddress = Util.publicKeyToAddress(otherNodeKeyPair.getPublicKey());
    validatorList.add(otherAddress);
    validatorList.add(AddressHelpers.ofValue(1));

    genesisBlock = createEmptyBlock(proposerKeyPair);

    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    blockChain =
        new DefaultMutableBlockchain(
            genesisBlock, keyValueStorage, MainnetBlockHashFunction::createHash);

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));
    final VoteProposer voteProposer = new VoteProposer();
    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer);
    cliqueProtocolContext = new ProtocolContext<>(blockChain, null, cliqueContext);

    headerBuilder.parentHash(genesisBlock.getHash()).number(1);
    final Block block_1 = createEmptyBlock(proposerKeyPair);
    blockChain.appendBlock(block_1, Lists.newArrayList());

    headerBuilder.parentHash(block_1.getHash()).number(2);
    final Block block_2 = createEmptyBlock(otherNodeKeyPair);
    blockChain.appendBlock(block_2, Lists.newArrayList());

    headerBuilder.parentHash(block_2.getHash()).number(3);
    final Block block_3 = createEmptyBlock(otherNodeKeyPair);
    blockChain.appendBlock(block_3, Lists.newArrayList());

    assertThat(
            CliqueHelpers.addressIsAllowedToProduceNextBlock(
                localAddress, cliqueProtocolContext, block_1.getHeader()))
        .isFalse();
    assertThat(
            CliqueHelpers.addressIsAllowedToProduceNextBlock(
                localAddress, cliqueProtocolContext, block_2.getHeader()))
        .isTrue();
    assertThat(
            CliqueHelpers.addressIsAllowedToProduceNextBlock(
                localAddress, cliqueProtocolContext, block_3.getHeader()))
        .isTrue();
  }

  @Test
  public void signerIsValidIfInsufficientBlocksExistInHistory() {
    final Address otherAddress = Util.publicKeyToAddress(otherNodeKeyPair.getPublicKey());
    validatorList.add(otherAddress);
    validatorList.add(AddressHelpers.ofValue(1));
    validatorList.add(AddressHelpers.ofValue(2));
    validatorList.add(AddressHelpers.ofValue(3));
    // Should require 2 blocks between signings.

    genesisBlock = createEmptyBlock(proposerKeyPair);

    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    blockChain =
        new DefaultMutableBlockchain(
            genesisBlock, keyValueStorage, MainnetBlockHashFunction::createHash);

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));
    final VoteProposer voteProposer = new VoteProposer();
    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer);
    cliqueProtocolContext = new ProtocolContext<>(blockChain, null, cliqueContext);

    headerBuilder.parentHash(genesisBlock.getHash()).number(1);
    final Block block_1 = createEmptyBlock(otherNodeKeyPair);
    blockChain.appendBlock(block_1, Lists.newArrayList());

    assertThat(
            CliqueHelpers.addressIsAllowedToProduceNextBlock(
                localAddress, cliqueProtocolContext, genesisBlock.getHeader()))
        .isTrue();
    assertThat(
            CliqueHelpers.addressIsAllowedToProduceNextBlock(
                localAddress, cliqueProtocolContext, block_1.getHeader()))
        .isTrue();
  }

  @Test
  public void exceptionIsThrownIfOnAnOrphanedChain() {
    final Address otherAddress = Util.publicKeyToAddress(otherNodeKeyPair.getPublicKey());
    validatorList.add(otherAddress);

    genesisBlock = createEmptyBlock(proposerKeyPair);

    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    blockChain =
        new DefaultMutableBlockchain(
            genesisBlock, keyValueStorage, MainnetBlockHashFunction::createHash);

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));
    final VoteProposer voteProposer = new VoteProposer();
    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer);
    cliqueProtocolContext = new ProtocolContext<>(blockChain, null, cliqueContext);

    headerBuilder.parentHash(Hash.ZERO).number(3);
    final BlockHeader parentHeader =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, otherNodeKeyPair, validatorList);

    assertThatThrownBy(
            () ->
                CliqueHelpers.addressIsAllowedToProduceNextBlock(
                    localAddress, cliqueProtocolContext, parentHeader))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("The block was on a orphaned chain.");
  }

  @Test
  public void nonValidatorIsNotAllowedToCreateABlock() {
    genesisBlock = createEmptyBlock(otherNodeKeyPair);

    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    blockChain =
        new DefaultMutableBlockchain(
            genesisBlock, keyValueStorage, MainnetBlockHashFunction::createHash);

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));
    final VoteProposer voteProposer = new VoteProposer();
    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer);
    cliqueProtocolContext = new ProtocolContext<>(blockChain, null, cliqueContext);

    headerBuilder.parentHash(Hash.ZERO).number(3);
    final BlockHeader parentHeader = headerBuilder.buildHeader();
    assertThat(
            CliqueHelpers.addressIsAllowedToProduceNextBlock(
                AddressHelpers.ofValue(1), cliqueProtocolContext, parentHeader))
        .isFalse();
  }
}
