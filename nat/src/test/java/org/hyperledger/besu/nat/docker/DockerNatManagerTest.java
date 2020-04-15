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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.nat.core.exception.NatInitializationException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class DockerNatManagerTest {

  private final String advertisedHost = "99.45.69.12";
  private final String detectedAdvertisedHost = "199.45.69.12";

  private final int p2pPort = 1;
  private final int rpcHttpPort = 2;

  @Mock private HostBasedIpDetector hostBasedIpDetector;

  private DockerNatManager natManager;

  @Before
  public void initialize() throws NatInitializationException {
    hostBasedIpDetector = mock(HostBasedIpDetector.class);
    when(hostBasedIpDetector.detectExternalIp()).thenReturn(Optional.of(detectedAdvertisedHost));
    natManager = new DockerNatManager(hostBasedIpDetector, advertisedHost, p2pPort, rpcHttpPort);
    natManager.start();
  }

  @Test
  public void assertThatExternalIPIsEqualToRemoteHost()
      throws ExecutionException, InterruptedException {
    assertThat(natManager.queryExternalIPAddress().get()).isEqualTo(detectedAdvertisedHost);
  }

  @Test
  public void assertThatExternalIPIsEqualToDefaultHostIfIpDetectorCannotRetrieveIP()
      throws ExecutionException, InterruptedException {
    when(hostBasedIpDetector.detectExternalIp()).thenReturn(Optional.empty());
    assertThat(natManager.queryExternalIPAddress().get()).isEqualTo(advertisedHost);
  }

  @Test
  public void assertThatLocalIPIsEqualToLocalHost()
      throws ExecutionException, InterruptedException, UnknownHostException {
    final String internalHost = InetAddress.getLocalHost().getHostAddress();
    assertThat(natManager.queryLocalIPAddress().get()).isEqualTo(internalHost);
  }

  @Test
  public void assertThatMappingForDiscoveryWorks() throws UnknownHostException {
    final String internalHost = InetAddress.getLocalHost().getHostAddress();

    final NatPortMapping mapping =
        natManager.getPortMapping(NatServiceType.DISCOVERY, NetworkProtocol.UDP);

    final NatPortMapping expectedMapping =
        new NatPortMapping(
            NatServiceType.DISCOVERY,
            NetworkProtocol.UDP,
            internalHost,
            detectedAdvertisedHost,
            p2pPort,
            p2pPort);

    assertThat(mapping).isEqualToComparingFieldByField(expectedMapping);
  }

  @Test
  public void assertThatMappingForJsonRpcWorks() throws UnknownHostException {
    final String internalHost = InetAddress.getLocalHost().getHostAddress();

    final NatPortMapping mapping =
        natManager.getPortMapping(NatServiceType.JSON_RPC, NetworkProtocol.TCP);

    final NatPortMapping expectedMapping =
        new NatPortMapping(
            NatServiceType.JSON_RPC,
            NetworkProtocol.TCP,
            internalHost,
            detectedAdvertisedHost,
            rpcHttpPort,
            rpcHttpPort);

    assertThat(mapping).isEqualToComparingFieldByField(expectedMapping);
  }

  @Test
  public void assertThatMappingForRlpxWorks() throws UnknownHostException {
    final String internalHost = InetAddress.getLocalHost().getHostAddress();

    final NatPortMapping mapping =
        natManager.getPortMapping(NatServiceType.RLPX, NetworkProtocol.TCP);

    final NatPortMapping expectedMapping =
        new NatPortMapping(
            NatServiceType.RLPX,
            NetworkProtocol.TCP,
            internalHost,
            detectedAdvertisedHost,
            p2pPort,
            p2pPort);

    assertThat(mapping).isEqualToComparingFieldByField(expectedMapping);
  }
}
