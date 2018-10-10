package net.consensys.pantheon.ethereum.eth.manager.ethtaskutils;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.eth.manager.EthPeer;

public abstract class RetryingMessageTaskWithResultsTest<T> extends RetryingMessageTaskTest<T, T> {

  @Override
  protected void assertResultMatchesExpectation(
      final T requestedData, final T response, final EthPeer respondingPeer) {
    assertThat(response).isEqualTo(requestedData);
  }
}
