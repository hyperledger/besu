package net.consensys.pantheon.consensus.clique.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.consensus.clique.VoteTallyCache;
import net.consensys.pantheon.consensus.common.VoteTally;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.AddressHelpers;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class CliqueProposerSelectorTest {

  private final List<Address> validatorList =
      Arrays.asList(
          AddressHelpers.ofValue(1),
          AddressHelpers.ofValue(2),
          AddressHelpers.ofValue(3),
          AddressHelpers.ofValue(4));
  private final VoteTally voteTally = new VoteTally(validatorList);
  private VoteTallyCache voteTallyCache;

  @Before
  public void setup() {
    voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(voteTally);
  }

  @Test
  public void proposerForABlockIsBasedOnModBlockNumber() {
    final BlockHeaderTestFixture headerBuilderFixture = new BlockHeaderTestFixture();

    for (int prevBlockNumber = 0; prevBlockNumber < 10; prevBlockNumber++) {
      headerBuilderFixture.number(prevBlockNumber);
      final CliqueProposerSelector selector = new CliqueProposerSelector(voteTallyCache);
      final Address nextProposer =
          selector.selectProposerForNextBlock(headerBuilderFixture.buildHeader());
      assertThat(nextProposer)
          .isEqualTo(validatorList.get((prevBlockNumber + 1) % validatorList.size()));
    }
  }
}
