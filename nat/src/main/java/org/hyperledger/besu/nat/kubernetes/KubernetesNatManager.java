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

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class describes the behaviour of the Kubernetes NAT manager. Kubernetes Nat manager add
 * support for Kubernetes’s NAT implementation when Besu is being run from a Kubernetes cluster
 */
public class KubernetesNatManager extends AbstractNatManager {
  protected static final Logger LOG = LogManager.getLogger();

  private static final String KUBE_CONFIG_PATH_ENV = "KUBE_CONFIG_PATH";
  private static final String DEFAULT_KUBE_CONFIG_PATH = "~/.kube/config";
  private static final String DEFAULT_BESU_POD_NAME_FILTER = "besu";

  private String internalAdvertisedHost;
  private final List<NatPortMapping> forwardedPorts = new ArrayList<>();

  public KubernetesNatManager() {
    super(NatMethod.KUBERNETES);
  }

  @Override
  protected void doStart() {
    LOG.info("Starting kubernetes NAT manager.");
    update();
  }

  private void update() {
    try {
      LOG.debug("Trying to update information using Kubernetes client SDK.");
      final String kubeConfigPath =
          Optional.ofNullable(System.getenv(KUBE_CONFIG_PATH_ENV)).orElse(DEFAULT_KUBE_CONFIG_PATH);
      LOG.debug(
          "Checking if Kubernetes config file is present on file system: {}.", kubeConfigPath);
      if (!Files.exists(Paths.get(kubeConfigPath))) {
        throw new IllegalStateException("Cannot locate Kubernetes config file.");
      }
      // loading the out-of-cluster config, a kubeconfig from file-system
      final ApiClient client =
          ClientBuilder.kubeconfig(
                  KubeConfig.loadKubeConfig(
                      Files.newBufferedReader(Paths.get(kubeConfigPath), Charset.defaultCharset())))
              .build();

      // set the global default api-client to the in-cluster one from above
      Configuration.setDefaultApiClient(client);

      // the CoreV1Api loads default api-client from global configuration.
      CoreV1Api api = new CoreV1Api();
      // invokes the CoreV1Api client
      api.listServiceForAllNamespaces(null, null, null, null, null, null, null, null, null)
          .getItems().stream()
          .filter(
              v1Service -> v1Service.getMetadata().getName().contains(DEFAULT_BESU_POD_NAME_FILTER))
          .findFirst()
          .ifPresent(this::updateUsingBesuService);

    } catch (Exception e) {
      LOG.warn("Failed update information using Kubernetes client SDK.", e);
    }
  }

  @VisibleForTesting
  void updateUsingBesuService(final V1Service service) {
    try {
      LOG.info("Found Besu service: {}", service.getMetadata().getName());
      LOG.info("Setting host IP to: {}.", service.getSpec().getClusterIP());
      internalAdvertisedHost = service.getSpec().getClusterIP();
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
      LOG.warn("Failed update information using pod metadata.", e);
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
