/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason.TIMEOUT;
import static org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason.USELESS_PEER_USELESS_RESPONSES;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.eth.messages.EthPV62;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PeerReputationTest {

  private static final int INITIAL_SCORE = 25;
  private static final int MAX_SCORE = 50;
  private final PeerReputation reputation = new PeerReputation(INITIAL_SCORE, MAX_SCORE);
  private final EthPeer mockEthPeer = mock(EthPeer.class);

  @Test
  public void shouldThrowOnInvalidInitialScore() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new PeerReputation(2, 1));
  }

  @Test
  public void shouldOnlyDisconnectWhenTimeoutLimitReached() {
    sendRequestTimeouts(EthPV62.GET_BLOCK_HEADERS, PeerReputation.TIMEOUT_THRESHOLD - 1);
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS, mockEthPeer))
        .contains(TIMEOUT);
  }

  @Test
  public void shouldTrackTimeoutsSeparatelyForDifferentRequestTypes() {
    sendRequestTimeouts(EthPV62.GET_BLOCK_HEADERS, PeerReputation.TIMEOUT_THRESHOLD - 1);
    sendRequestTimeouts(EthPV62.GET_BLOCK_BODIES, PeerReputation.TIMEOUT_THRESHOLD - 1);

    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS, mockEthPeer))
        .contains(TIMEOUT);
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_BODIES, mockEthPeer))
        .contains(TIMEOUT);
  }

  @Test
  public void shouldResetTimeoutCountForRequestType() {
    sendRequestTimeouts(EthPV62.GET_BLOCK_HEADERS, PeerReputation.TIMEOUT_THRESHOLD - 1);
    sendRequestTimeouts(EthPV62.GET_BLOCK_BODIES, PeerReputation.TIMEOUT_THRESHOLD - 1);

    reputation.resetTimeoutCount(EthPV62.GET_BLOCK_HEADERS);
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS, mockEthPeer)).isEmpty();
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_BODIES, mockEthPeer))
        .contains(TIMEOUT);
  }

  @Test
  public void shouldOnlyDisconnectWhenEmptyResponseThresholdReached() {
    sendUselessResponses(1001, PeerReputation.USELESS_RESPONSE_THRESHOLD - 1);
    assertThat(reputation.recordUselessResponse(1005, mockEthPeer))
        .contains(USELESS_PEER_USELESS_RESPONSES);
  }

  @Test
  public void shouldDiscardEmptyResponseRecordsAfterTimeWindowElapses() {
    // Bring it to the brink of disconnection.
    sendUselessResponses(1001, PeerReputation.USELESS_RESPONSE_THRESHOLD - 1);

    // But then the next empty response doesn't come in until after the window expires on the first
    assertThat(
            reputation.recordUselessResponse(
                1001 + PeerReputation.USELESS_RESPONSE_WINDOW_IN_MILLIS + 1, mockEthPeer))
        .isEmpty();
  }

  @Test
  public void shouldIncreaseScore() {
    reputation.recordUsefulResponse();
    assertThat(reputation.getScore()).isGreaterThan(INITIAL_SCORE);
  }

  @Test
  public void shouldNotIncreaseScoreOverMax() {
    for (int i = 0; i <= MAX_SCORE + 1; i++) {
      reputation.recordUsefulResponse();
    }
    assertThat(reputation.getScore()).isEqualTo(MAX_SCORE);
  }

  private void sendRequestTimeouts(final int requestType, final int repeatCount) {
    for (int i = 0; i < repeatCount; i++) {
      assertThat(reputation.recordRequestTimeout(requestType, mockEthPeer)).isEmpty();
    }
  }

  private void sendUselessResponses(final long timestamp, final int repeatCount) {
    for (int i = 0; i < repeatCount; i++) {
      assertThat(reputation.recordUselessResponse(timestamp + i, mockEthPeer)).isEmpty();
    }
  }
}
