package net.consensys.pantheon.ethereum.mainnet.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

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
public class GasUsageValidationRuleTest {

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {5, 6, true}, // gasUsed is less than gasLimit is valid
          {5, 5, true}, // gasUsed is the same as gaslimit is valid
          {5, 4, false}, // gasUsed is less than gasLimit
        });
  }

  @Parameter public long gasUsed;

  @Parameter(1)
  public long gasLimit;

  @Parameter(2)
  public boolean expectedResult;

  @Test
  public void test() {
    final GasUsageValidationRule uut = new GasUsageValidationRule();
    final BlockHeaderTestFixture blockBuilder = new BlockHeaderTestFixture();

    blockBuilder.gasLimit(gasLimit);
    blockBuilder.gasUsed(gasUsed);

    final BlockHeader header = blockBuilder.buildHeader();

    assertThat(uut.validate(header, null)).isEqualTo(expectedResult);
  }
}
