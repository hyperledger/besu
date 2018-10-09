package net.consensys.pantheon.consensus.clique.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.consensus.clique.CliqueContext;
import net.consensys.pantheon.consensus.clique.TestHelpers;
import net.consensys.pantheon.consensus.clique.VoteTallyCache;
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
import org.junit.Test;

public class SignerRateLimitValidationRuleTest {

  private final KeyPair proposerKeyPair = KeyPair.generate();
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

  @Test
  public void networkWithOneValidatorIsAllowedToCreateConsecutiveBlocks() {
    final Address localAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());
    validatorList.add(localAddress);

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

    final BlockHeader nextBlockHeader =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, proposerKeyPair, validatorList);

    final SignerRateLimitValidationRule validationRule = new SignerRateLimitValidationRule();

    assertThat(
            validationRule.validate(
                nextBlockHeader, genesisBlock.getHeader(), cliqueProtocolContext))
        .isTrue();
  }

  @Test
  public void networkWithTwoValidatorsIsAllowedToProduceBlockIfNotPreviousBlockProposer() {
    final Address localAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());
    final Address otherAddress = Util.publicKeyToAddress(otherNodeKeyPair.getPublicKey());
    validatorList.add(localAddress);
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

    final BlockHeader nextBlockHeader =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, proposerKeyPair, validatorList);

    final SignerRateLimitValidationRule validationRule = new SignerRateLimitValidationRule();

    assertThat(
            validationRule.validate(
                nextBlockHeader, genesisBlock.getHeader(), cliqueProtocolContext))
        .isTrue();
  }

  @Test
  public void networkWithTwoValidatorsIsNotAllowedToProduceBlockIfIsPreviousBlockProposer() {
    final Address localAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());
    final Address otherAddress = Util.publicKeyToAddress(otherNodeKeyPair.getPublicKey());
    validatorList.add(localAddress);
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
    final Address localAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());
    final Address otherAddress = Util.publicKeyToAddress(otherNodeKeyPair.getPublicKey());
    validatorList.add(localAddress);
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
    blockChain.appendBlock(block_1, Lists.newArrayList());

    final SignerRateLimitValidationRule validationRule = new SignerRateLimitValidationRule();
    BlockHeader nextBlockHeader;

    // Should not be able to proposer ontop of Block_1 (which has the same sealer)
    headerBuilder.parentHash(block_1.getHash()).number(2);
    nextBlockHeader =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, proposerKeyPair, validatorList);
    assertThat(validationRule.validate(nextBlockHeader, block_1.getHeader(), cliqueProtocolContext))
        .isFalse();

    headerBuilder.parentHash(block_1.getHash()).number(3);
    nextBlockHeader =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, proposerKeyPair, validatorList);
    assertThat(validationRule.validate(nextBlockHeader, block_2.getHeader(), cliqueProtocolContext))
        .isTrue();
  }

  @Test
  public void signerIsValidIfInsufficientBlocksExistInHistory() {
    final Address localAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());
    final Address otherAddress = Util.publicKeyToAddress(otherNodeKeyPair.getPublicKey());
    validatorList.add(localAddress);
    validatorList.add(otherAddress);
    validatorList.add(AddressHelpers.ofValue(1));

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

    final BlockHeader nextBlockHeader =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, proposerKeyPair, validatorList);

    final SignerRateLimitValidationRule validationRule = new SignerRateLimitValidationRule();

    assertThat(
            validationRule.validate(
                nextBlockHeader, genesisBlock.getHeader(), cliqueProtocolContext))
        .isTrue();
  }

  @Test
  public void exceptionIsThrownIfOnAnOrphanedChain() {
    final Address localAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());
    final Address otherAddress = Util.publicKeyToAddress(otherNodeKeyPair.getPublicKey());
    validatorList.add(localAddress);
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

    headerBuilder.parentHash(Hash.ZERO).number(4);
    final BlockHeader nextBlock =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, proposerKeyPair, validatorList);

    headerBuilder.parentHash(Hash.ZERO).number(3);
    final BlockHeader parentHeader =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilder, otherNodeKeyPair, validatorList);

    final SignerRateLimitValidationRule validationRule = new SignerRateLimitValidationRule();

    assertThatThrownBy(
            () -> validationRule.validate(nextBlock, parentHeader, cliqueProtocolContext))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("The block was on a orphaned chain.");
  }
}
