package net.consensys.pantheon.ethereum.rlp;

import static org.junit.Assert.assertEquals;

import net.consensys.pantheon.testutil.JsonTestParameters;

import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** The Ethereum reference RLP tests. */
@RunWith(Parameterized.class)
public class RLPRefTest {

  private static final String TEST_CONFIG_FILES = "RLPTests/rlptest.json";

  private final RLPRefTestCaseSpec spec;

  public RLPRefTest(final String name, final RLPRefTestCaseSpec spec) {
    this.spec = spec;
  }

  @Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(RLPRefTestCaseSpec.class).generate(TEST_CONFIG_FILES);
  }

  @Test
  public void encode() {
    assertEquals(spec.getOut(), RLP.encode(spec.getIn()));
  }

  @Test
  public void decode() {
    assertEquals(spec.getIn(), RLP.decode(spec.getOut()));
  }
}
