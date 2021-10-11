package org.hyperledger.besu.consensus.common.bft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.JsonBftConfigOptions;

import java.util.List;

import org.junit.Test;

public class BftForksScheduleTest {

  @Test
  public void retrievesGenesisFork() {
    final BftForkSpec<BftConfigOptions> genesisForkSpec =
        new BftForkSpec<>(0, JsonBftConfigOptions.DEFAULT);

    final BftForksSchedule<BftConfigOptions> schedule =
        new BftForksSchedule<>(genesisForkSpec, List.of());
    assertThat(schedule.getFork(0)).isEqualTo(genesisForkSpec);
    assertThat(schedule.getFork(1)).isEqualTo(genesisForkSpec);
  }

  @Test
  public void retrievesLatestFork() {
    final BftForkSpec<BftConfigOptions> genesisForkSpec =
        new BftForkSpec<>(0, JsonBftConfigOptions.DEFAULT);
    final BftForkSpec<BftConfigOptions> forkSpec1 = createForkSpec(1, 10);
    final BftForkSpec<BftConfigOptions> forkSpec2 = createForkSpec(2, 20);

    final BftForksSchedule<BftConfigOptions> schedule =
        new BftForksSchedule<>(genesisForkSpec, List.of(forkSpec1, forkSpec2));

    assertThat(schedule.getFork(0)).isEqualTo(genesisForkSpec);
    assertThat(schedule.getFork(1)).isEqualTo(forkSpec1);
    assertThat(schedule.getFork(2)).isEqualTo(forkSpec2);
    assertThat(schedule.getFork(3)).isEqualTo(forkSpec2);
  }

  @Test
  public void throwsErrorIfHasForkForGenesisBlock() {
    final BftForkSpec<BftConfigOptions> genesisForkSpec =
        new BftForkSpec<>(0, JsonBftConfigOptions.DEFAULT);
    final BftForkSpec<BftConfigOptions> fork = createForkSpec(0, 10);

    assertThatThrownBy(() -> new BftForksSchedule<>(genesisForkSpec, List.of(fork)))
        .hasMessage("Transition cannot be created for genesis block");
  }

  @Test
  public void throwsErrorIfHasForksWithDuplicateBlock() {
    final BftForkSpec<BftConfigOptions> genesisForkSpec =
        new BftForkSpec<>(0, JsonBftConfigOptions.DEFAULT);
    final BftForkSpec<BftConfigOptions> fork1 = createForkSpec(1, 10);
    final BftForkSpec<BftConfigOptions> fork2 = createForkSpec(1, 10);
    final BftForkSpec<BftConfigOptions> fork3 = createForkSpec(3, 10);

    assertThatThrownBy(() -> new BftForksSchedule<>(genesisForkSpec, List.of(fork1, fork2, fork3)))
        .hasMessage("Duplicate transitions cannot be created for the same block");
  }

  private BftForkSpec<BftConfigOptions> createForkSpec(
      final long block, final int blockPeriodSeconds) {
    final MutableBftConfigOptions bftConfigOptions =
        new MutableBftConfigOptions(JsonBftConfigOptions.DEFAULT);
    bftConfigOptions.setBlockPeriodSeconds(blockPeriodSeconds);
    return new BftForkSpec<>(block, bftConfigOptions);
  }
}
