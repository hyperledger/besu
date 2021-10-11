package org.hyperledger.besu.consensus.qbft.validator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.JsonQbftConfigOptions;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.config.QbftFork.VALIDATOR_SELECTION_MODE;
import org.hyperledger.besu.consensus.common.bft.BftForkSpec;
import org.hyperledger.besu.consensus.common.bft.BftForksSchedule;
import org.hyperledger.besu.consensus.qbft.MutableQbftConfigOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

public class QbftForksSchedulesFactoryTest {

  @Test
  public void createsScheduleForJustGenesisConfig() {
    final MutableQbftConfigOptions qbftConfigOptions =
        new MutableQbftConfigOptions(JsonQbftConfigOptions.DEFAULT);
    final BftForkSpec<QbftConfigOptions> expectedForkSpec = new BftForkSpec<>(0, qbftConfigOptions);

    final BftForksSchedule<QbftConfigOptions> forksSchedule =
        QbftForksSchedulesFactory.create(qbftConfigOptions, List.of());
    assertThat(forksSchedule.getFork(0)).usingRecursiveComparison().isEqualTo(expectedForkSpec);
    assertThat(forksSchedule.getFork(1)).usingRecursiveComparison().isEqualTo(expectedForkSpec);
    assertThat(forksSchedule.getFork(2)).usingRecursiveComparison().isEqualTo(expectedForkSpec);
  }

  @Test
  public void createsScheduleWithForkThatOverridesGenesisValues() {
    final MutableQbftConfigOptions configOptions =
        new MutableQbftConfigOptions(JsonQbftConfigOptions.DEFAULT);

    final QbftFork fork =
        new QbftFork(
            JsonUtil.objectNodeFromMap(
                Map.of(
                    BftFork.FORK_BLOCK_KEY,
                    1,
                    BftFork.VALIDATORS_KEY,
                    List.of("1", "2", "3"),
                    BftFork.BLOCK_PERIOD_SECONDS_KEY,
                    10,
                    BftFork.BLOCK_REWARD_KEY,
                    "5",
                    QbftFork.VALIDATOR_SELECTION_MODE_KEY,
                    VALIDATOR_SELECTION_MODE.CONTRACT,
                    QbftFork.VALIDATOR_CONTRACT_ADDRESS_KEY,
                    "10")));

    final BftForksSchedule<QbftConfigOptions> forksSchedule =
        QbftForksSchedulesFactory.create(configOptions, List.of(fork));
    assertThat(forksSchedule.getFork(0))
        .usingRecursiveComparison()
        .isEqualTo(new BftForkSpec<>(0, configOptions));

    final Map<String, Object> forkOptions = new HashMap<>(configOptions.asMap());
    forkOptions.put(BftFork.BLOCK_PERIOD_SECONDS_KEY, 10);
    forkOptions.put(BftFork.BLOCK_REWARD_KEY, "5");
    forkOptions.put(QbftFork.VALIDATOR_SELECTION_MODE_KEY, "5");
    forkOptions.put(QbftFork.VALIDATOR_CONTRACT_ADDRESS_KEY, "10");
    final QbftConfigOptions expectedForkConfig =
        new MutableQbftConfigOptions(
            new JsonQbftConfigOptions(JsonUtil.objectNodeFromMap(forkOptions)));

    final BftForkSpec<QbftConfigOptions> expectedFork = new BftForkSpec<>(1, expectedForkConfig);
    assertThat(forksSchedule.getFork(1)).usingRecursiveComparison().isEqualTo(expectedFork);
    assertThat(forksSchedule.getFork(2)).usingRecursiveComparison().isEqualTo(expectedFork);
  }

  @Test
  public void creatingScheduleThrowsErrorForContractForkWithoutContractAddress() {
    final MutableQbftConfigOptions configOptions =
        new MutableQbftConfigOptions(JsonQbftConfigOptions.DEFAULT);

    final QbftFork fork =
        new QbftFork(
            JsonUtil.objectNodeFromMap(
                Map.of(
                    BftFork.FORK_BLOCK_KEY,
                    1,
                    QbftFork.VALIDATOR_SELECTION_MODE_KEY,
                    VALIDATOR_SELECTION_MODE.CONTRACT)));

    assertThatThrownBy(() -> QbftForksSchedulesFactory.create(configOptions, List.of(fork)))
        .hasMessage("QBFT transition has config with contract mode but no contract address");
  }

  @Test
  public void switchingToBlockHeaderRemovesValidatorContractAddress() {
    final MutableQbftConfigOptions configOptions =
        new MutableQbftConfigOptions(JsonQbftConfigOptions.DEFAULT);
    configOptions.setValidatorContractAddress(Optional.of("10"));

    final QbftFork fork =
        new QbftFork(
            JsonUtil.objectNodeFromMap(
                Map.of(
                    BftFork.FORK_BLOCK_KEY,
                    1,
                    QbftFork.VALIDATOR_SELECTION_MODE_KEY,
                    VALIDATOR_SELECTION_MODE.BLOCKHEADER)));

    final BftForksSchedule<QbftConfigOptions> forksSchedule =
        QbftForksSchedulesFactory.create(configOptions, List.of(fork));

    assertThat(forksSchedule.getFork(1).getConfigOptions().getValidatorContractAddress()).isEmpty();
  }
}
