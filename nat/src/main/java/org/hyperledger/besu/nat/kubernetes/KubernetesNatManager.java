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

import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.core.AbstractNatManager;
import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.nat.core.exception.NatInitializationException;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1LoadBalancerIngress;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.authenticators.GCPAuthenticator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class describes the behaviour of the Kubernetes NAT manager. Kubernetes Nat manager add
 * support for Kubernetes’s NAT implementation when Besu is being run from a Kubernetes cluster
 */
public class KubernetesNatManager extends AbstractNatManager {
  private static final Logger LOG = LogManager.getLogger();

  private static final String DEFAULT_BESU_POD_NAME_FILTER = "besu";

  private String internalAdvertisedHost;
  private final List<NatPortMapping> forwardedPorts = new ArrayList<>();

  public KubernetesNatManager() {
    super(NatMethod.KUBERNETES);
  }

  @Override
  protected void doStart() throws NatInitializationException {
    LOG.info("Starting kubernetes NAT manager.");
    try {

      KubeConfig.registerAuthenticator(new GCPAuthenticator());

      LOG.debug("Trying to update information using Kubernetes client SDK.");
      final ApiClient client = ClientBuilder.cluster().build();

      // set the global default api-client to the in-cluster one from above
      Configuration.setDefaultApiClient(client);

      // the CoreV1Api loads default api-client from global configuration.
      final CoreV1Api api = new CoreV1Api();
      // invokes the CoreV1Api client
      final V1Service service =
          api.listServiceForAllNamespaces(null, null, null, null, null, null, null, null, null)
              .getItems().stream()
              .filter(
                  v1Service ->
                      v1Service.getMetadata().getName().contains(DEFAULT_BESU_POD_NAME_FILTER))
              .findFirst()
              .orElseThrow(() -> new NatInitializationException("Service not found"));
      updateUsingBesuService(service);
    } catch (Exception e) {
      throw new NatInitializationException(
          "Failed update information using Kubernetes client SDK.", e);
    }
  }

  @VisibleForTesting
  void updateUsingBesuService(final V1Service service) throws RuntimeException {
    try {
      LOG.info("Found Besu service: {}", service.getMetadata().getName());

      final V1LoadBalancerIngress v1LoadBalancerIngress =
          service.getStatus().getLoadBalancer().getIngress().stream()
              .filter(
                  v1LoadBalancerIngress1 ->
                      v1LoadBalancerIngress1.getHostname() != null
                          || v1LoadBalancerIngress1.getIp() != null)
              .findFirst()
              .orElseThrow(() -> new NatInitializationException("Ingress not found"));

      if (v1LoadBalancerIngress.getHostname() != null) {
        internalAdvertisedHost =
            InetAddress.getByName(v1LoadBalancerIngress.getHostname()).getHostAddress();
      } else {
        internalAdvertisedHost = v1LoadBalancerIngress.getIp();
      }

      LOG.info("Setting host IP to: {}.", internalAdvertisedHost);

      final String internalHost = queryLocalIPAddress().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      service
          .getSpec()
          .getPorts()
          .forEach(
              v1ServicePort -> {
                try {
                  final NatServiceType natServiceType =
                      NatServiceType.fromString(v1ServicePort.getName());
                  forwardedPorts.add(
                      new NatPortMapping(
                          natServiceType,
                          natServiceType.equals(NatServiceType.DISCOVERY)
                              ? NetworkProtocol.UDP
                              : NetworkProtocol.TCP,
                          internalHost,
                          internalAdvertisedHost,
                          v1ServicePort.getPort(),
                          v1ServicePort.getTargetPort().getIntValue()));
                } catch (IllegalStateException e) {
                  LOG.warn("Ignored unknown Besu port: {}", e.getMessage());
                }
              });
    } catch (Exception e) {
      throw new RuntimeException("Failed update information using pod metadata.", e);
    }
  }

  @Override
  protected void doStop() {
    LOG.info("Stopping kubernetes NAT manager.");
  }

  @Override
  protected CompletableFuture<String> retrieveExternalIPAddress() {
    return CompletableFuture.completedFuture(internalAdvertisedHost);
  }

  @Override
  public CompletableFuture<List<NatPortMapping>> getPortMappings() {
    return CompletableFuture.completedFuture(forwardedPorts);
  }
}
