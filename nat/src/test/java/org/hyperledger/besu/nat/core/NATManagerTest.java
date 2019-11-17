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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.nat.core.domain.NATMethod;
import org.hyperledger.besu.nat.core.domain.NATPortMapping;
import org.hyperledger.besu.nat.core.domain.NATServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.nat.upnp.UpnpNatSystem;

import java.util.Optional;

import org.assertj.core.api.Assertions;
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

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Test
  public void assertThatGetPortMappingWorksProperlyWithUpNp() {
    final String externalIp = "127.0.0.3";
    final NATPortMapping natPortMapping =
        new NATPortMapping(
            NATServiceType.DISCOVERY, NetworkProtocol.UDP, externalIp, externalIp, 1111, 1111);
    final NATSystem natSystem = mock(NATSystem.class);
    when(natSystem.getPortMapping(natPortMapping.getNatServiceType(), natPortMapping.getProtocol()))
        .thenReturn(natPortMapping);
    when(natSystem.getNatMethod()).thenReturn(NATMethod.UPNP);

    final NATManager natManager = new NATManager(NATMethod.UPNP);
    natManager.setNatSystem(natSystem);

    final Optional<NATPortMapping> portMapping =
        natManager.getPortMapping(natPortMapping.getNatServiceType(), natPortMapping.getProtocol());

    verify(natSystem)
        .getPortMapping(natPortMapping.getNatServiceType(), natPortMapping.getProtocol());

    Assertions.assertThat(portMapping.get()).isEqualTo(natPortMapping);
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Test
  public void assertThatGetPortMappingWorksProperlyWithoutNat() {

    final NATManager natManager = new NATManager(NATMethod.NONE);

    final Optional<NATPortMapping> portMapping =
        natManager.getPortMapping(NATServiceType.DISCOVERY, NetworkProtocol.TCP);

    Assertions.assertThat(portMapping).isNotPresent();
  }
}
