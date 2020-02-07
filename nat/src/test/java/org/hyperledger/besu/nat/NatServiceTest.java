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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NatServiceTest {

  @Test
  public void assertThatGetNatManagerReturnValidManager() {
    final NatService natService = new NatService(Optional.of(new UpnpNatManager()));
    assertThat(natService.getNatMethod()).isEqualTo(NatMethod.UPNP);
    assertThat(natService.getNatManager()).containsInstanceOf(UpnpNatManager.class);
  }

  @Test
  public void assertThatGetNatManagerNotReturnManagerWhenNatMethodIsNone() {
    final NatService natService = new NatService(Optional.empty());
    assertThat(natService.getNatMethod()).isEqualTo(NatMethod.NONE);
    assertThat(natService.getNatManager()).isNotPresent();
  }

  @Test
  public void assertThatIsNatEnvironmentReturnCorrectStatus() {
    final NatService nonNatService = new NatService(Optional.empty());
    assertThat(nonNatService.isNatEnvironment()).isFalse();

    final NatService upnpNatService = new NatService(Optional.of(new UpnpNatManager()));
    assertThat(upnpNatService.isNatEnvironment()).isTrue();
  }

  @Test
  public void assertThatGetPortMappingWorksProperlyWithUpNp() {
    final String externalIp = "127.0.0.3";
    final NatPortMapping natPortMapping =
        new NatPortMapping(
            NatServiceType.DISCOVERY, NetworkProtocol.UDP, externalIp, externalIp, 1111, 1111);
    final NatManager natManager = mock(NatManager.class);
    when(natManager.getPortMapping(
            natPortMapping.getNatServiceType(), natPortMapping.getProtocol()))
        .thenReturn(natPortMapping);
    when(natManager.getNatMethod()).thenReturn(NatMethod.UPNP);

    final NatService natService = new NatService(Optional.of(natManager));

    final Optional<NatPortMapping> portMapping =
        natService.getPortMapping(natPortMapping.getNatServiceType(), natPortMapping.getProtocol());

    verify(natManager)
        .getPortMapping(natPortMapping.getNatServiceType(), natPortMapping.getProtocol());

    assertThat(portMapping).contains(natPortMapping);
  }

  @Test
  public void assertThatGetPortMappingWorksProperlyWithoutNat() {

    final NatService natService = new NatService(Optional.empty());

    final Optional<NatPortMapping> portMapping =
        natService.getPortMapping(NatServiceType.DISCOVERY, NetworkProtocol.TCP);

    assertThat(portMapping).isNotPresent();
  }

  @Test
  public void assertQueryExternalIpWorksProperlyWithUpNp() {
    final String externalIp = "127.0.0.3";
    final NatManager natManager = mock(NatManager.class);
    when(natManager.queryExternalIPAddress())
        .thenReturn(CompletableFuture.completedFuture(externalIp));
    when(natManager.getNatMethod()).thenReturn(NatMethod.UPNP);

    final NatService natService = new NatService(Optional.of(natManager));

    final Optional<String> resultIp = natService.queryExternalIPAddress();

    verify(natManager).queryExternalIPAddress();

    assertThat(resultIp).containsSame(externalIp);
  }

  @Test
  public void assertThatQueryExternalIpWorksProperlyWithoutNat() {

    final NatService natService = new NatService(Optional.empty());

    final Optional<String> resultIp = natService.queryExternalIPAddress();

    assertThat(resultIp).isNotPresent();
  }

  @Test
  public void assertThatQueryLocalIPAddressWorksProperlyWithUpNp() {
    final String externalIp = "127.0.0.3";
    final NatManager natManager = mock(NatManager.class);
    when(natManager.queryLocalIPAddress())
        .thenReturn(CompletableFuture.completedFuture(externalIp));
    when(natManager.getNatMethod()).thenReturn(NatMethod.UPNP);

    final NatService natService = new NatService(Optional.of(natManager));

    final Optional<String> resultIp = natService.queryLocalIPAddress();

    verify(natManager).queryLocalIPAddress();

    assertThat(resultIp).containsSame(externalIp);
  }

  @Test
  public void assertThatQueryLocalIPAddressWorksProperlyWithoutNat() {

    final NatService natService = new NatService(Optional.empty());

    final Optional<String> resultIp = natService.queryLocalIPAddress();

    assertThat(resultIp).isNotPresent();
  }

  @Test
  public void givenOneAutoDetectionWorksWhenAutoDetectThenReturnCorrectNatMethod() {
    final NatMethod natMethod = NatService.autoDetectNatMethod(() -> Optional.of(NatMethod.UPNP));
    assertThat(natMethod).isEqualTo(NatMethod.UPNP);
  }

  @Test
  public void givenNoAutoDetectionWorksWhenAutoDetectThenReturnEmptyNatMethod() {
    final NatMethod natMethod = NatService.autoDetectNatMethod(Optional::empty);
    assertThat(natMethod).isEqualTo(NatMethod.NONE);
  }
}
