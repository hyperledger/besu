package tech.pegasys.pantheon.ethereum.development;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

public class DevelopmentProtocolScheduleTest {

  @Test
  public void reportedDifficultyForAllBlocksIsAFixedValue() {

    final JsonObject config = new JsonObject();
    final ProtocolSchedule<Void> schedule = DevelopmentProtocolSchedule.create(config);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    final BlockHeader parentHeader = headerBuilder.number(1).buildHeader();

    assertThat(
            schedule
                .getByBlockNumber(0)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(DevelopmentDifficultyCalculators.MINIMUM_DIFFICULTY);

    assertThat(
            schedule
                .getByBlockNumber(500)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(DevelopmentDifficultyCalculators.MINIMUM_DIFFICULTY);

    assertThat(
            schedule
                .getByBlockNumber(500_000)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(DevelopmentDifficultyCalculators.MINIMUM_DIFFICULTY);
  }
}
