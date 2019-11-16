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

package org.hyperledger.besu.nat.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.nat.core.domain.NATMethod;
import org.hyperledger.besu.nat.upnp.UpnpNatSystem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NATManagerTest {

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Test
  public void assertThatGetNatSystemReturnValidSystem() {
    final NATManager natManager = new NATManager(NATMethod.UPNP);
    assertThat(natManager.getNatSystem().get()).isInstanceOf(UpnpNatSystem.class);
  }

  @Test
  public void assertThatGetNatSystemNotReturnSystemWhenNatMethodIsNone() {
    final NATManager natManager = new NATManager(NATMethod.NONE);
    assertThat(natManager.getNatSystem()).isNotPresent();
  }

  @Test
  public void assertThatIsNatEnvironmentReturnCorrectStatus() {
    final NATManager nonNatManager = new NATManager(NATMethod.NONE);
    assertThat(nonNatManager.isNATEnvironment()).isFalse();

    final NATManager upnpNatManager = new NATManager(NATMethod.UPNP);
    assertThat(upnpNatManager.isNATEnvironment()).isTrue();
  }
}
