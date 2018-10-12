package tech.pegasys.pantheon.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import tech.pegasys.pantheon.ethereum.core.MiningParametersTestBuilder;
import tech.pegasys.pantheon.ethereum.core.PendingTransactions;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.time.SystemClock;

import java.util.concurrent.Executors;

import org.junit.Test;

public class EthHashMinerExecutorTest {

  @Test
  public void startingMiningWithoutCoinbaseThrowsException() {
    final MiningParameters miningParameters =
        new MiningParametersTestBuilder().coinbase(null).build();

    final EthHashMinerExecutor executor =
        new EthHashMinerExecutor(
            null,
            Executors.newCachedThreadPool(),
            null,
            new PendingTransactions(1),
            miningParameters,
            new DefaultBlockScheduler(1, 10, new SystemClock()));

    assertThatExceptionOfType(CoinbaseNotSetException.class)
        .isThrownBy(() -> executor.startAsyncMining(new Subscribers<>(), null))
        .withMessageContaining("Unable to start mining without a coinbase.");
  }

  @Test
  public void settingCoinbaseToNullThrowsException() {
    final MiningParameters miningParameters = new MiningParametersTestBuilder().build();

    final EthHashMinerExecutor executor =
        new EthHashMinerExecutor(
            null,
            Executors.newCachedThreadPool(),
            null,
            new PendingTransactions(1),
            miningParameters,
            new DefaultBlockScheduler(1, 10, new SystemClock()));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> executor.setCoinbase(null))
        .withMessageContaining("Coinbase cannot be unset.");
  }
}
