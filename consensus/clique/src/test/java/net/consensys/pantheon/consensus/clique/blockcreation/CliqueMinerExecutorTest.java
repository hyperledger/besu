package net.consensys.pantheon.consensus.clique.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.consensus.clique.CliqueContext;
import net.consensys.pantheon.consensus.clique.CliqueExtraData;
import net.consensys.pantheon.consensus.clique.CliqueProtocolSchedule;
import net.consensys.pantheon.consensus.clique.VoteTallyCache;
import net.consensys.pantheon.consensus.common.EpochManager;
import net.consensys.pantheon.consensus.common.VoteProposer;
import net.consensys.pantheon.consensus.common.VoteTally;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.MiningParameters;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.AddressHelpers;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.core.Util;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;

import com.google.common.collect.Lists;
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

public class CliqueMinerExecutorTest {

  private final KeyPair proposerKeyPair = KeyPair.generate();
  private Address localAddress;
  private final List<Address> validatorList = Lists.newArrayList();
  private ProtocolContext<CliqueContext> cliqueProtocolContext;
  private BlockHeaderTestFixture blockHeaderBuilder;

  @Before
  public void setup() {
    localAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());
    validatorList.add(localAddress);
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddress, 1));
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddress, 2));
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddress, 3));

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));
    final VoteProposer voteProposer = new VoteProposer();

    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer);
    cliqueProtocolContext = new ProtocolContext<>(null, null, cliqueContext);
    blockHeaderBuilder = new BlockHeaderTestFixture();
  }

  @Test
  public void extraDataCreatedOnEpochBlocksContainsValidators() {
    byte[] vanityData = new byte[32];
    new Random().nextBytes(vanityData);
    final BytesValue wrappedVanityData = BytesValue.wrap(vanityData);
    final int EPOCH_LENGTH = 10;

    final CliqueMinerExecutor executor =
        new CliqueMinerExecutor(
            cliqueProtocolContext,
            Executors.newSingleThreadExecutor(),
            CliqueProtocolSchedule.create(new JsonObject(), proposerKeyPair),
            new PendingTransactions(1),
            proposerKeyPair,
            new MiningParameters(AddressHelpers.ofValue(1), Wei.ZERO, wrappedVanityData, false),
            mock(CliqueBlockScheduler.class),
            new EpochManager(EPOCH_LENGTH));

    // NOTE: Passing in the *parent* block, so must be 1 less than EPOCH
    final BlockHeader header = blockHeaderBuilder.number(EPOCH_LENGTH - 1).buildHeader();

    final BytesValue extraDataBytes = executor.calculateExtraData(header);

    final CliqueExtraData cliqueExtraData = CliqueExtraData.decode(extraDataBytes);

    assertThat(cliqueExtraData.getVanityData()).isEqualTo(wrappedVanityData);
    assertThat(cliqueExtraData.getValidators())
        .containsExactly(validatorList.toArray(new Address[0]));
  }

  @Test
  public void extraDataForNonEpochBlocksDoesNotContainValidaors() {
    byte[] vanityData = new byte[32];
    new Random().nextBytes(vanityData);
    final BytesValue wrappedVanityData = BytesValue.wrap(vanityData);
    final int EPOCH_LENGTH = 10;

    final CliqueMinerExecutor executor =
        new CliqueMinerExecutor(
            cliqueProtocolContext,
            Executors.newSingleThreadExecutor(),
            CliqueProtocolSchedule.create(new JsonObject(), proposerKeyPair),
            new PendingTransactions(1),
            proposerKeyPair,
            new MiningParameters(AddressHelpers.ofValue(1), Wei.ZERO, wrappedVanityData, false),
            mock(CliqueBlockScheduler.class),
            new EpochManager(EPOCH_LENGTH));

    // Parent block was epoch, so the next block should contain no validators.
    final BlockHeader header = blockHeaderBuilder.number(EPOCH_LENGTH).buildHeader();

    final BytesValue extraDataBytes = executor.calculateExtraData(header);

    final CliqueExtraData cliqueExtraData = CliqueExtraData.decode(extraDataBytes);

    assertThat(cliqueExtraData.getVanityData()).isEqualTo(wrappedVanityData);
    assertThat(cliqueExtraData.getValidators()).isEqualTo(Lists.newArrayList());
  }
}
