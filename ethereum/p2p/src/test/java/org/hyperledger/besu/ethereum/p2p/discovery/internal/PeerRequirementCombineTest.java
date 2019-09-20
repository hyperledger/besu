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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PeerRequirementCombineTest {
  private static final PeerRequirement fulfilled = () -> true;
  private static final PeerRequirement notFulfilled = () -> false;

  private static final AtomicBoolean configurableIsFulfilled = new AtomicBoolean(true);
  private static final PeerRequirement configurable = configurableIsFulfilled::get;

  private final List<PeerRequirement> requirements;
  private final boolean expectedResult;

  public PeerRequirementCombineTest(
      final List<PeerRequirement> requirements, final boolean expectedResult) {
    this.requirements = requirements;
    this.expectedResult = expectedResult;
  }

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {Collections.emptyList(), true},
          {Arrays.asList(fulfilled), true},
          {Arrays.asList(notFulfilled), false},
          {Arrays.asList(notFulfilled, notFulfilled), false},
          {Arrays.asList(notFulfilled, fulfilled), false},
          {Arrays.asList(fulfilled, notFulfilled), false},
          {Arrays.asList(fulfilled, fulfilled), true}
        });
  }

  @Test
  public void combine() {
    PeerRequirement combined = PeerRequirement.combine(requirements);
    assertThat(combined.hasSufficientPeers()).isEqualTo(expectedResult);
  }

  @Test
  public void combineAndModify() {
    List<PeerRequirement> modifiableRequirements = new ArrayList<>(requirements);
    modifiableRequirements.add(configurable);

    PeerRequirement combined = PeerRequirement.combine(modifiableRequirements);
    assertThat(combined.hasSufficientPeers()).isEqualTo(expectedResult);

    // If the configurable requirement switches to false, we should always get false
    configurableIsFulfilled.set(false);
    assertThat(combined.hasSufficientPeers()).isFalse();

    // Otherwise, we should get our expected result
    configurableIsFulfilled.set(true);
    assertThat(combined.hasSufficientPeers()).isEqualTo(expectedResult);
  }

  @Test
  public void combine_withOn() {
    PeerRequirement combined = PeerRequirement.combine(Collections.emptyList());
    assertThat(combined.hasSufficientPeers()).isTrue();
  }
}
