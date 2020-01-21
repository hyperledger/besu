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

package org.hyperledger.besu.nat.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.nat.core.AutoDetectionResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DockerNatManagerTest {

  @Mock private DockerAutoDetection dockerDetector;

  @Test
  public void givenNormalConditions_whenDetectorReturnsTrue_assertCorrectAutoDetectionResult() {
    when(dockerDetector.shouldBeThisNatMethod())
        .thenReturn(new AutoDetectionResult(NatMethod.DOCKER, true));
    assertThat(NatService.autoDetectNatMethod(dockerDetector)).isEqualTo(NatMethod.DOCKER);
  }

  @Test
  public void givenNormalConditions_whenDetectorReturnsFalse_assertCorrectAutoDetectionResult() {
    when(dockerDetector.shouldBeThisNatMethod())
        .thenReturn(new AutoDetectionResult(NatMethod.DOCKER, false));
    assertThat(NatService.autoDetectNatMethod(dockerDetector)).isEqualTo(NatMethod.NONE);
  }
}
