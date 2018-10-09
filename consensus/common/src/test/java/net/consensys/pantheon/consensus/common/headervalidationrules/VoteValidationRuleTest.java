package net.consensys.pantheon.consensus.common.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.consensus.common.VoteType;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VoteValidationRuleTest {

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {VoteType.DROP.getNonceValue(), true},
          {VoteType.ADD.getNonceValue(), true},
          {0x01L, false},
          {0xFFFFFFFFFFFFFFFEL, false}
        });
  }

  @Parameter public long actualVote;

  @Parameter(1)
  public boolean expectedResult;

  @Test
  public void test() {
    final VoteValidationRule uut = new VoteValidationRule();
    final BlockHeaderTestFixture blockBuilder = new BlockHeaderTestFixture();
    blockBuilder.nonce(actualVote);

    final BlockHeader header = blockBuilder.buildHeader();

    assertThat(uut.validate(header, null)).isEqualTo(expectedResult);
  }
}
