package org.hyperledger.besu.consensus.qbt.test;

import static org.junit.Assume.assumeTrue;

import org.hyperledger.besu.consensus.qbt.support.extradata.BftExtraDataTestCaseSpec;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.util.Collection;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BftExtraDataRlpTest {
  private static final String TEST_CONFIG_PATH = "BftExtraDataRLPTests/";
  private final BftExtraDataTestCaseSpec spec;

  @Parameterized.Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(BftExtraDataTestCaseSpec.class).generate(TEST_CONFIG_PATH);
  }

  public BftExtraDataRlpTest(
      final String name, final BftExtraDataTestCaseSpec spec, final boolean runTest) {
    this.spec = spec;
    assumeTrue("Test was blacklisted", runTest);
  }

  @Test
  public void encode() {
    final Map<String, Object> bftExtraData = spec.getBftExtraData();
    // TODO: Convert using our custom objectmapper which deserialize BftExtraData
    Assertions.assertThat(bftExtraData).isNotEmpty();
    System.out.println(bftExtraData);
  }
}
