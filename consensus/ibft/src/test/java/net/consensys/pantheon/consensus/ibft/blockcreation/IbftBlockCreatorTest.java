package net.consensys.pantheon.consensus.ibft.blockcreation;

import static net.consensys.pantheon.ethereum.core.InMemoryWorldState.createInMemoryWorldStateArchive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.consensus.common.VoteProposer;
import net.consensys.pantheon.consensus.common.VoteTally;
import net.consensys.pantheon.consensus.ibft.IbftBlockHeaderValidationRulesetFactory;
import net.consensys.pantheon.consensus.ibft.IbftContext;
import net.consensys.pantheon.consensus.ibft.IbftExtraData;
import net.consensys.pantheon.consensus.ibft.IbftProtocolSchedule;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.chain.MutableBlockchain;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import net.consensys.pantheon.ethereum.mainnet.HeaderValidationMode;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

public class IbftBlockCreatorTest {

  @Test
  public void headerProducedPassesValidationRules() {
    // Construct a parent block.
    final BlockHeaderTestFixture blockHeaderBuilder = new BlockHeaderTestFixture();
    blockHeaderBuilder.gasLimit(5000); // required to pass validation rule checks.
    final BlockHeader parentHeader = blockHeaderBuilder.buildHeader();
    final Optional<BlockHeader> optionalHeader = Optional.of(parentHeader);

    // Construct a block chain and world state
    final MutableBlockchain blockchain = mock(MutableBlockchain.class);
    when(blockchain.getChainHeadHash()).thenReturn(parentHeader.getHash());
    when(blockchain.getBlockHeader(any())).thenReturn(optionalHeader);

    final KeyPair nodeKeys = KeyPair.generate();
    // Add the local node as a validator (can't propose a block if node is not a validator).
    final Address localAddr = Address.extract(Hash.hash(nodeKeys.getPublicKey().getEncodedBytes()));
    final List<Address> initialValidatorList =
        Arrays.asList(
            Address.fromHexString(String.format("%020d", 1)),
            Address.fromHexString(String.format("%020d", 2)),
            Address.fromHexString(String.format("%020d", 3)),
            Address.fromHexString(String.format("%020d", 4)),
            localAddr);

    final VoteTally voteTally = new VoteTally(initialValidatorList);

    final ProtocolSchedule<IbftContext> protocolSchedule =
        IbftProtocolSchedule.create(new JsonObject("{\"spuriousDragonBlock\":0}"));
    final ProtocolContext<IbftContext> protContext =
        new ProtocolContext<>(
            blockchain,
            createInMemoryWorldStateArchive(),
            new IbftContext(voteTally, new VoteProposer()));

    final IbftBlockCreator blockCreator =
        new IbftBlockCreator(
            Address.fromHexString(String.format("%020d", 0)),
            parent ->
                new IbftExtraData(
                        BytesValue.wrap(new byte[32]),
                        Lists.newArrayList(),
                        null,
                        initialValidatorList)
                    .encode(),
            new PendingTransactions(1),
            protContext,
            protocolSchedule,
            parentGasLimit -> parentGasLimit,
            nodeKeys,
            Wei.ZERO,
            parentHeader);

    final Block block = blockCreator.createBlock(Instant.now().getEpochSecond());

    final BlockHeaderValidator<IbftContext> rules =
        IbftBlockHeaderValidationRulesetFactory.ibftProposedBlockValidator(0);

    final boolean validationResult =
        rules.validateHeader(
            block.getHeader(), parentHeader, protContext, HeaderValidationMode.FULL);

    assertThat(validationResult).isTrue();
  }
}
