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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.nat.core.NatManager;
import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.nat.core.exception.NatInitializationException;
import org.hyperledger.besu.nat.upnp.UpnpNatManager;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NatServiceTest {

  @Test
  public void assertThatGetNatManagerReturnValidManager() {
    final NatService natService = new NatService(Optional.of(new UpnpNatManager()), true);
    assertThat(natService.getNatMethod()).isEqualTo(NatMethod.UPNP);
    assertThat(natService.getNatManager()).containsInstanceOf(UpnpNatManager.class);
  }

  @Test
  public void assertThatGetNatManagerNotReturnManagerWhenNatMethodIsNone() {
    final NatService natService = new NatService(Optional.empty(), true);
    assertThat(natService.getNatMethod()).isEqualTo(NatMethod.NONE);
    assertThat(natService.getNatManager()).isNotPresent();
  }

  @Test
  public void assertThatIsNatEnvironmentReturnCorrectStatus() {
    final NatService nonNatService = new NatService(Optional.empty(), true);
    assertThat(nonNatService.isNatEnvironment()).isFalse();

    final NatService upnpNatService = new NatService(Optional.of(new UpnpNatManager()), true);
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

    final NatService natService = new NatService(Optional.of(natManager), true);

    final Optional<NatPortMapping> portMapping =
        natService.getPortMapping(natPortMapping.getNatServiceType(), natPortMapping.getProtocol());

    verify(natManager)
        .getPortMapping(natPortMapping.getNatServiceType(), natPortMapping.getProtocol());

    assertThat(portMapping).contains(natPortMapping);
  }

  @Test
  public void assertThatGetPortMappingWorksProperlyWithoutNat() {

    final NatService natService = new NatService(Optional.empty(), true);

    final Optional<NatPortMapping> portMapping =
        natService.getPortMapping(NatServiceType.DISCOVERY, NetworkProtocol.TCP);

    assertThat(portMapping).isNotPresent();
  }

  @Test
  public void assertQueryExternalIpWorksProperlyWithUpNp() {
    final String fallbackExternalIp = "127.0.0.1";
    final String externalIp = "127.0.0.3";
    final NatManager natManager = mock(NatManager.class);
    when(natManager.queryExternalIPAddress())
        .thenReturn(CompletableFuture.completedFuture(externalIp));
    when(natManager.getNatMethod()).thenReturn(NatMethod.UPNP);

    final NatService natService = new NatService(Optional.of(natManager), true);

    final String resultIp = natService.queryExternalIPAddress(fallbackExternalIp);

    verify(natManager).queryExternalIPAddress();

    assertThat(resultIp).isEqualTo(externalIp);
  }

  @Test
  public void assertQueryExternalIpWorksProperlyWithUpNpP2pOnly() {
    final String fallbackExternalIp = "127.0.0.1";
    final String externalIp = "127.0.0.3";
    final NatManager natManager = mock(NatManager.class);
    when(natManager.queryExternalIPAddress())
        .thenReturn(CompletableFuture.completedFuture(externalIp));
    when(natManager.getNatMethod()).thenReturn(NatMethod.UPNPP2PONLY);

    final NatService natService = new NatService(Optional.of(natManager), true);

    final String resultIp = natService.queryExternalIPAddress(fallbackExternalIp);

    verify(natManager).queryExternalIPAddress();

    assertThat(resultIp).isEqualTo(externalIp);
  }

  @Test
  public void assertThatQueryExternalIpWorksProperlyWithoutNat() {

    final String fallbackExternalIp = "127.0.0.1";

    final NatService natService = new NatService(Optional.empty(), true);

    final String resultIp = natService.queryExternalIPAddress(fallbackExternalIp);

    assertThat(resultIp).isEqualTo(fallbackExternalIp);
  }

  @Test
  public void assertThatQueryLocalIPAddressWorksProperlyWithUpNp() {
    final String fallbackExternalIp = "127.0.0.1";
    final String externalIp = "127.0.0.3";
    final NatManager natManager = mock(NatManager.class);
    when(natManager.queryLocalIPAddress())
        .thenReturn(CompletableFuture.completedFuture(externalIp));
    when(natManager.getNatMethod()).thenReturn(NatMethod.UPNP);

    final NatService natService = new NatService(Optional.of(natManager), true);

    final String resultIp = natService.queryLocalIPAddress(fallbackExternalIp);

    verify(natManager).queryLocalIPAddress();

    assertThat(resultIp).isEqualTo(externalIp);
  }

  @Test
  public void assertThatQueryLocalIPAddressWorksProperlyWithoutNat() {

    final String fallbackValue = "1.2.3.4";

    final NatService natService = new NatService(Optional.empty(), true);

    final String resultIp = natService.queryLocalIPAddress(fallbackValue);

    assertThat(resultIp).isEqualTo(fallbackValue);
  }

  @Test
  public void assertThatManagerSwitchToNoneForInvalidNatEnvironment()
      throws NatInitializationException {

    final String externalIp = "1.2.3.4";
    final String localIp = "2.2.3.4";
    final String fallbackExternalIp = "3.4.5.6";
    final String fallbackLocalIp = "4.4.5.6";

    final NatManager natManager = mock(NatManager.class);
    doThrow(NatInitializationException.class).when(natManager).start();
    when(natManager.queryExternalIPAddress())
        .thenReturn(CompletableFuture.completedFuture(externalIp));
    when(natManager.queryLocalIPAddress()).thenReturn(CompletableFuture.completedFuture(localIp));
    when(natManager.getPortMapping(any(NatServiceType.class), any(NetworkProtocol.class)))
        .thenReturn(
            new NatPortMapping(
                NatServiceType.DISCOVERY, NetworkProtocol.UDP, localIp, externalIp, 1111, 1111));
    when(natManager.getNatMethod()).thenReturn(NatMethod.UPNP);

    final NatService natService = new NatService(Optional.of(natManager), true);

    assertThat(natService.getNatMethod()).isEqualTo(NatMethod.UPNP);
    assertThat(natService.isNatEnvironment()).isTrue();
    assertThat(natService.getNatManager()).contains(natManager);
    assertThat(natService.getPortMapping(NatServiceType.DISCOVERY, NetworkProtocol.UDP))
        .isPresent();
    assertThat(natService.queryExternalIPAddress(fallbackExternalIp)).isEqualTo(externalIp);
    assertThat(natService.queryLocalIPAddress(fallbackLocalIp)).isEqualTo(localIp);

    natService.start();

    assertThat(natService.getNatMethod()).isEqualTo(NatMethod.NONE);
    assertThat(natService.isNatEnvironment()).isFalse();
    assertThat(natService.getNatManager()).isNotPresent();
    assertThat(natService.getPortMapping(NatServiceType.DISCOVERY, NetworkProtocol.UDP))
        .isNotPresent();
    assertThat(natService.queryExternalIPAddress(fallbackExternalIp)).isEqualTo(fallbackExternalIp);
    assertThat(natService.queryLocalIPAddress(fallbackLocalIp)).isEqualTo(fallbackLocalIp);
  }

  @Test
  public void assertThatManagerSwitchToNoneForInvalidNatEnvironmentIfFallbackDisabled()
      throws NatInitializationException {

    final NatManager natManager = mock(NatManager.class);
    doThrow(NatInitializationException.class).when(natManager).start();

    when(natManager.getNatMethod()).thenReturn(NatMethod.UPNP);

    final NatService natService = new NatService(Optional.of(natManager), false);

    assertThat(natService.getNatMethod()).isEqualTo(NatMethod.UPNP);
    assertThat(natService.isNatEnvironment()).isTrue();
    assertThat(natService.getNatManager()).contains(natManager);

    assertThatThrownBy(natService::start);
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
