/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.consensus.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TransitionBestPeerComparatorTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  EthPeer a;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  EthPeer b;

  @Test
  public void assertDistanceFromTTDPrecedence() {
    var comparator = new TransitionBestPeerComparator(Difficulty.of(5000));
    when(a.chainState().getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(5002));
    when(b.chainState().getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(4995));
    // a has less distance from TTD:
    assertThat(comparator.compare(a, b)).isEqualTo(1);
    when(b.chainState().getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(5001));
    // b has less distance from TTD:
    assertThat(comparator.compare(a, b)).isEqualTo(-1);
    when(b.chainState().getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(5002));
    // a and b are equi-distant
    assertThat(comparator.compare(a, b)).isEqualTo(0);
  }

  @Test
  public void assertHandlesNewTTD() {
    var comparator = new TransitionBestPeerComparator(Difficulty.of(5000));
    when(a.chainState().getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(5002));
    when(b.chainState().getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(4999));
    assertThat(comparator.compare(a, b)).isEqualTo(-1);

    // update TTD with actual value
    comparator.mergeStateChanged(true, Optional.empty(), Optional.of(Difficulty.of(5002)));
    assertThat(comparator.compare(a, b)).isEqualTo(1);
  }
}
