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
import static org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason.USELESS_PEER;

import org.hyperledger.besu.ethereum.eth.messages.EthPV62;

import org.junit.Test;

public class PeerReputationTest {

  private final PeerReputation reputation = new PeerReputation();

  @Test
  public void shouldOnlyDisconnectWhenTimeoutLimitReached() {
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS)).contains(TIMEOUT);
  }

  @Test
  public void shouldTrackTimeoutsSeparatelyForDifferentRequestTypes() {
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_BODIES)).isEmpty();
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_BODIES)).isEmpty();

    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS)).contains(TIMEOUT);
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_BODIES)).contains(TIMEOUT);
  }

  @Test
  public void shouldResetTimeoutCountForRequestType() {
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS)).isEmpty();

    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_BODIES)).isEmpty();
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_BODIES)).isEmpty();

    reputation.resetTimeoutCount(EthPV62.GET_BLOCK_HEADERS);
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_HEADERS)).isEmpty();
    assertThat(reputation.recordRequestTimeout(EthPV62.GET_BLOCK_BODIES)).contains(TIMEOUT);
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
    assertThat(
            reputation.recordUselessResponse(
                1001 + PeerReputation.USELESS_RESPONSE_WINDOW_IN_MILLIS + 1))
        .isEmpty();
  }

  @Test
  public void shouldIncreaseScore() {
    reputation.recordUsefulResposne();
    assertThat(reputation.compareTo(new PeerReputation())).isGreaterThan(0);
  }
}
