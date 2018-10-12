package net.consensys.pantheon.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.core.ExecutionContextTestFixture;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.mainnet.EthHashSolution;
import net.consensys.pantheon.util.bytes.Bytes32;

import java.util.Optional;

import org.junit.Test;

public class EthHashMiningCoordinatorTest {

  private final ExecutionContextTestFixture executionContext = new ExecutionContextTestFixture();

  @Test
  public void miningCoordinatorIsCreatedDisabledWithNoReportableMiningStatistics() {
    final EthHashMinerExecutor executor = mock(EthHashMinerExecutor.class);
    final EthHashMiningCoordinator miningCoordinator =
        new EthHashMiningCoordinator(executionContext.getBlockchain(), executor);
    final EthHashSolution solution = new EthHashSolution(1L, Hash.EMPTY, new byte[Bytes32.SIZE]);

    assertThat(miningCoordinator.isRunning()).isFalse();
    assertThat(miningCoordinator.hashesPerSecond()).isEqualTo(Optional.empty());
    assertThat(miningCoordinator.getWorkDefinition()).isEqualTo(Optional.empty());
    assertThat(miningCoordinator.submitWork(solution)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void reportedHashRateIsCachedIfNoCurrentDataInMiner() throws InterruptedException {

    final EthHashBlockMiner miner = mock(EthHashBlockMiner.class);

    final Optional<Long> hashRate1 = Optional.of(10L);
    final Optional<Long> hashRate2 = Optional.empty();
    final Optional<Long> hashRate3 = Optional.of(20L);

    when(miner.getHashesPerSecond()).thenReturn(hashRate1, hashRate2, hashRate3);

    final EthHashMinerExecutor executor = mock(EthHashMinerExecutor.class);
    when(executor.startAsyncMining(any(), any())).thenReturn(miner);

    final EthHashMiningCoordinator miningCoordinator =
        new EthHashMiningCoordinator(executionContext.getBlockchain(), executor);

    miningCoordinator.enable(); // Must enable prior returning data
    assertThat(miningCoordinator.hashesPerSecond()).isEqualTo(hashRate1);
    assertThat(miningCoordinator.hashesPerSecond()).isEqualTo(hashRate1);
    assertThat(miningCoordinator.hashesPerSecond()).isEqualTo(hashRate3);
  }
}
