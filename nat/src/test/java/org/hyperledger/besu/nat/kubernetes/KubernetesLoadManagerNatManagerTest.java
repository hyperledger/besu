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
package org.hyperledger.besu.nat.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.nat.kubernetes.KubernetesNatManager.DEFAULT_BESU_SERVICE_NAME_FILTER;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1LoadBalancerStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1ServiceStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class KubernetesLoadManagerNatManagerTest {

  private final String detectedAdvertisedHost = "199.45.69.12";

  private final int p2pPort = 1;
  private final int rpcHttpPort = 2;

  @Mock private V1Service v1Service;

  private KubernetesNatManager natManager;

  @Before
  public void initialize() throws IOException {
    final V1ServiceStatus v1ServiceStatus =
        new V1ServiceStatus()
            .loadBalancer(
                new V1LoadBalancerStatus()
                    .addIngressItem(new V1LoadBalancerIngress().ip(detectedAdvertisedHost)));
    when(v1Service.getStatus()).thenReturn(v1ServiceStatus);
    when(v1Service.getSpec())
        .thenReturn(
            new V1ServiceSpec()
                .type("LoadBalancer")
                .ports(
                    Arrays.asList(
                        new V1ServicePort()
                            .name(NatServiceType.JSON_RPC.getValue())
                            .port(rpcHttpPort)
                            .targetPort(new IntOrString(rpcHttpPort)),
                        new V1ServicePort()
                            .name(NatServiceType.RLPX.getValue())
                            .port(p2pPort)
                            .targetPort(new IntOrString(p2pPort)),
                        new V1ServicePort()
                            .name(NatServiceType.DISCOVERY.getValue())
                            .port(p2pPort)
                            .targetPort(new IntOrString(p2pPort)))));
    when(v1Service.getMetadata())
        .thenReturn(new V1ObjectMeta().name(DEFAULT_BESU_SERVICE_NAME_FILTER));
    natManager = new KubernetesNatManager(DEFAULT_BESU_SERVICE_NAME_FILTER);
    try {
      natManager.start();
    } catch (Exception ignored) {
      System.err.println("Ignored missing Kube config file in testing context.");
    }
    natManager.updateUsingBesuService(v1Service);
  }

  @Test
  public void assertThatExternalIPIsEqualToRemoteHost()
      throws ExecutionException, InterruptedException {

    assertThat(natManager.queryExternalIPAddress().get()).isEqualTo(detectedAdvertisedHost);
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

    assertThat(mapping).usingRecursiveComparison().isEqualTo(expectedMapping);
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

    assertThat(mapping).usingRecursiveComparison().isEqualTo(expectedMapping);
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

    assertThat(mapping).usingRecursiveComparison().isEqualTo(expectedMapping);
  }
}
