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

package org.hyperledger.besu.nat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.nat.core.NatManager;
import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.nat.upnp.UpnpNatManager;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NatServiceTest {

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Test
  public void assertThatGetNatSystemReturnValidSystem() {
    final NatService natService = new NatService(NatMethod.UPNP);
    assertThat(natService.getNatManager().get()).isInstanceOf(UpnpNatManager.class);
  }

  @Test
  public void assertThatGetNatSystemNotReturnSystemWhenNatMethodIsNone() {
    final NatService natService = new NatService(NatMethod.NONE);
    assertThat(natService.getNatManager()).isNotPresent();
  }

  @Test
  public void assertThatIsNatEnvironmentReturnCorrectStatus() {
    final NatService nonNatService = new NatService(NatMethod.NONE);
    assertThat(nonNatService.isNatEnvironment()).isFalse();

    final NatService upnpNatService = new NatService(NatMethod.UPNP);
    assertThat(upnpNatService.isNatEnvironment()).isTrue();
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Test
  public void assertThatGetPortMappingWorksProperlyWithUpNp() {
    final String externalIp = "127.0.0.3";
    final NatPortMapping natPortMapping =
        new NatPortMapping(
            NatServiceType.DISCOVERY, NetworkProtocol.UDP, externalIp, externalIp, 1111, 1111);
    final NatManager natSystem = mock(NatManager.class);
    when(natSystem.getPortMapping(natPortMapping.getNatServiceType(), natPortMapping.getProtocol()))
        .thenReturn(natPortMapping);

    final NatService natService = new NatService(NatMethod.UPNP);
    natService.setNatSystem(natSystem);

    final Optional<NatPortMapping> portMapping =
        natService.getPortMapping(natPortMapping.getNatServiceType(), natPortMapping.getProtocol());

    verify(natSystem)
        .getPortMapping(natPortMapping.getNatServiceType(), natPortMapping.getProtocol());

    Assertions.assertThat(portMapping.get()).isEqualTo(natPortMapping);
  }

  @Test
  public void assertThatGetPortMappingWorksProperlyWithoutNat() {

    final NatService natService = new NatService(NatMethod.NONE);

    final Optional<NatPortMapping> portMapping =
        natService.getPortMapping(NatServiceType.DISCOVERY, NetworkProtocol.TCP);

    Assertions.assertThat(portMapping).isNotPresent();
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Test
  public void assertQueryExternalIpWorksProperlyWithUpNp() {
    final String externalIp = "127.0.0.3";
    final NatManager natManager = mock(NatManager.class);
    when(natManager.queryExternalIPAddress())
        .thenReturn(CompletableFuture.completedFuture(externalIp));

    final NatService natService = new NatService(NatMethod.UPNP);
    natService.setNatSystem(natManager);

    final Optional<String> resultIp = natService.queryExternalIPAddress();

    verify(natManager).queryExternalIPAddress();

    Assertions.assertThat(resultIp.get()).isEqualTo(externalIp);
  }

  @Test
  public void assertThatQueryExternalIpWorksProperlyWithoutNat() {

    final NatService natService = new NatService(NatMethod.NONE);

    final Optional<String> resultIp = natService.queryExternalIPAddress();

    Assertions.assertThat(resultIp).isNotPresent();
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Test
  public void assertThatQueryLocalIPAddressWorksProperlyWithUpNp() {
    final String externalIp = "127.0.0.3";
    final NatManager natManager = mock(NatManager.class);
    when(natManager.queryLocalIPAddress())
        .thenReturn(CompletableFuture.completedFuture(externalIp));

    final NatService natService = new NatService(NatMethod.UPNP);
    natService.setNatSystem(natManager);

    final Optional<String> resultIp = natService.queryLocalIPAddress();

    verify(natManager).queryLocalIPAddress();

    Assertions.assertThat(resultIp.get()).isEqualTo(externalIp);
  }

  @Test
  public void assertThatQueryLocalIPAddressWorksProperlyWithoutNat() {

    final NatService natService = new NatService(NatMethod.NONE);

    final Optional<String> resultIp = natService.queryLocalIPAddress();

    Assertions.assertThat(resultIp).isNotPresent();
  }
}
