package tech.pegasys.pantheon.consensus.clique.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.clique.VoteTallyCache;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockScheduler.BlockCreationTimeResult;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.util.time.Clock;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class CliqueBlockSchedulerTest {

  private final KeyPair proposerKeyPair = KeyPair.generate();
  private Address localAddr;

  private final List<Address> validatorList = Lists.newArrayList();
  private VoteTallyCache voteTallyCache;
  private BlockHeaderTestFixture blockHeaderBuilder;

  @Before
  public void setup() {
    localAddr = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());

    validatorList.add(localAddr);
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddr, 1));

    voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(new VoteTally(validatorList));

    blockHeaderBuilder = new BlockHeaderTestFixture();
  }

  @Test
  public void inturnValidatorWaitsExactlyBlockInterval() {
    Clock clock = mock(Clock.class);
    final long currentSecondsSinceEpoch = 10L;
    final long secondsBetweenBlocks = 5L;
    when(clock.millisecondsSinceEpoch()).thenReturn(currentSecondsSinceEpoch * 1000);
    CliqueBlockScheduler scheduler =
        new CliqueBlockScheduler(clock, voteTallyCache, localAddr, secondsBetweenBlocks);

    // There are 2 validators, therefore block 2 will put localAddr as the in-turn voter, therefore
    // parent block should be number 1.
    BlockHeader parentHeader =
        blockHeaderBuilder.number(1).timestamp(currentSecondsSinceEpoch).buildHeader();

    BlockCreationTimeResult result = scheduler.getNextTimestamp(parentHeader);

    assertThat(result.getTimestampForHeader())
        .isEqualTo(currentSecondsSinceEpoch + secondsBetweenBlocks);
    assertThat(result.getMillisecondsUntilValid()).isEqualTo(secondsBetweenBlocks * 1000);
  }

  @Test
  public void outOfturnValidatorWaitsLongerThanBlockInterval() {
    Clock clock = mock(Clock.class);
    final long currentSecondsSinceEpoch = 10L;
    final long secondsBetweenBlocks = 5L;
    when(clock.millisecondsSinceEpoch()).thenReturn(currentSecondsSinceEpoch * 1000);
    CliqueBlockScheduler scheduler =
        new CliqueBlockScheduler(clock, voteTallyCache, localAddr, secondsBetweenBlocks);

    // There are 2 validators, therefore block 3 will put localAddr as the out-turn voter, therefore
    // parent block should be number 2.
    BlockHeader parentHeader =
        blockHeaderBuilder.number(2).timestamp(currentSecondsSinceEpoch).buildHeader();

    BlockCreationTimeResult result = scheduler.getNextTimestamp(parentHeader);

    assertThat(result.getTimestampForHeader())
        .isEqualTo(currentSecondsSinceEpoch + secondsBetweenBlocks);
    assertThat(result.getMillisecondsUntilValid()).isGreaterThan(secondsBetweenBlocks * 1000);
  }

  @Test
  public void inTurnValidatorCreatesBlockNowIFParentTimestampSufficientlyBehindNow() {
    Clock clock = mock(Clock.class);
    final long currentSecondsSinceEpoch = 10L;
    final long secondsBetweenBlocks = 5L;
    when(clock.millisecondsSinceEpoch()).thenReturn(currentSecondsSinceEpoch * 1000);
    CliqueBlockScheduler scheduler =
        new CliqueBlockScheduler(clock, voteTallyCache, localAddr, secondsBetweenBlocks);

    // There are 2 validators, therefore block 2 will put localAddr as the in-turn voter, therefore
    // parent block should be number 1.
    BlockHeader parentHeader =
        blockHeaderBuilder
            .number(1)
            .timestamp(currentSecondsSinceEpoch - secondsBetweenBlocks)
            .buildHeader();

    BlockCreationTimeResult result = scheduler.getNextTimestamp(parentHeader);

    assertThat(result.getTimestampForHeader()).isEqualTo(currentSecondsSinceEpoch);
    assertThat(result.getMillisecondsUntilValid()).isEqualTo(0);
  }
}
