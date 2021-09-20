package org.hyperledger.besu.consensus.merge;

import static org.assertj.core.api.Java6Assertions.assertThat;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import org.junit.Test;

public class MergeProtocolScheduleTest {

  @Test
  public void protocolSpecsAreCreatedAtBlockDefinedInJson() {
    final String jsonInput =
        "{\"config\": "
            + "{\"chainId\": 1,\n"
            + "\"homesteadBlock\": 1,\n"
            + "\"LondonBlock\": 1559}"
            + "}";

    final GenesisConfigOptions config = GenesisConfigFile.fromConfig(jsonInput).getConfigOptions();
    final ProtocolSchedule protocolSchedule = MergeProtocolSchedule.create(config, false);

    final ProtocolSpec homesteadSpec = protocolSchedule.getByBlockNumber(1);
    final ProtocolSpec londonSpec = protocolSchedule.getByBlockNumber(1559);

    assertThat(homesteadSpec.equals(londonSpec)).isFalse();
    assertThat(homesteadSpec.getFeeMarket().implementsBaseFee()).isFalse();
    assertThat(londonSpec.getFeeMarket().implementsBaseFee()).isTrue();
  }

  @Test
  public void parametersAlignWithMainnetWithAdjustments() {
    final ProtocolSpec london =
        MergeProtocolSchedule.create(GenesisConfigFile.DEFAULT.getConfigOptions(), false)
            .getByBlockNumber(0);

    assertThat(london.getName()).isEqualTo("Frontier");
    assertThat(london.getBlockReward()).isEqualTo(Wei.ZERO);
    assertThat(london.isSkipZeroBlockRewards()).isEqualTo(true);
  }
}
