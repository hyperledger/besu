package net.consensys.pantheon.ethereum.eth.manager;

import static net.consensys.pantheon.ethereum.eth.manager.PeerReputation.USELESS_RESPONSE_WINDOW_IN_MILLIS;
import static net.consensys.pantheon.ethereum.eth.messages.EthPV62.GET_BLOCK_BODIES;
import static net.consensys.pantheon.ethereum.eth.messages.EthPV62.GET_BLOCK_HEADERS;
import static net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason.TIMEOUT;
import static net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason.USELESS_PEER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class PeerReputationTest {

  private final PeerReputation reputation = new PeerReputation();

  @Test
  public void shouldOnlyDisconnectWhenTimeoutLimitReached() {
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_HEADERS)).contains(TIMEOUT);
  }

  @Test
  public void shouldTrackTimeoutsSeparatelyForDifferentRequestTypes() {
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_BODIES)).isEmpty();
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_BODIES)).isEmpty();

    assertThat(reputation.recordRequestTimeout(GET_BLOCK_HEADERS)).contains(TIMEOUT);
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_BODIES)).contains(TIMEOUT);
  }

  @Test
  public void shouldResetTimeoutCountForRequestType() {
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_HEADERS)).isEmpty();

    assertThat(reputation.recordRequestTimeout(GET_BLOCK_BODIES)).isEmpty();
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_BODIES)).isEmpty();

    reputation.resetTimeoutCount(GET_BLOCK_HEADERS);
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(GET_BLOCK_BODIES)).contains(TIMEOUT);
  }

  @Test
  public void shouldOnlyDisconnectWhenEmptyResponseThresholdReached() {
    assertThat(reputation.recordUselessResponse(1001)).isEmpty();
    assertThat(reputation.recordUselessResponse(1002)).isEmpty();
    assertThat(reputation.recordUselessResponse(1003)).isEmpty();
    assertThat(reputation.recordUselessResponse(1004)).isEmpty();
    assertThat(reputation.recordUselessResponse(1005)).contains(USELESS_PEER);
  }

  @Test
  public void shouldDiscardEmptyResponseRecordsAfterTimeWindowElapses() {
    // Bring it to the brink of disconnection.
    assertThat(reputation.recordUselessResponse(1001)).isEmpty();
    assertThat(reputation.recordUselessResponse(1002)).isEmpty();
    assertThat(reputation.recordUselessResponse(1003)).isEmpty();
    assertThat(reputation.recordUselessResponse(1004)).isEmpty();

    // But then the next empty response doesn't come in until after the window expires on the first
    assertThat(reputation.recordUselessResponse(1001 + USELESS_RESPONSE_WINDOW_IN_MILLIS + 1))
        .isEmpty();
  }
}
