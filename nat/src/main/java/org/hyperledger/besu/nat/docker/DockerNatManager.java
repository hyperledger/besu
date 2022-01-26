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

import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.core.AbstractNatManager;
import org.hyperledger.besu.nat.core.IpDetector;
import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.nat.core.exception.NatInitializationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class describes the behaviour of the Docker NAT manager. Docker Nat manager add support for
 * Docker’s NAT implementation when Besu is being run from a Docker container
 */
public class DockerNatManager extends AbstractNatManager {
  private static final Logger LOG = LoggerFactory.getLogger(DockerNatManager.class);

  private static final String PORT_MAPPING_TAG = "HOST_PORT_";

  private final IpDetector ipDetector;

  private final int internalP2pPort;
  private final int internalRpcHttpPort;

  private String internalAdvertisedHost;
  private final List<NatPortMapping> forwardedPorts = new ArrayList<>();

  public DockerNatManager(final String advertisedHost, final int p2pPort, final int rpcHttpPort) {
    this(new HostBasedIpDetector(), advertisedHost, p2pPort, rpcHttpPort);
  }

  public DockerNatManager(
      final IpDetector ipDetector,
      final String advertisedHost,
      final int p2pPort,
      final int rpcHttpPort) {
    super(NatMethod.DOCKER);
    this.ipDetector = ipDetector;
    this.internalAdvertisedHost = advertisedHost;
    this.internalP2pPort = p2pPort;
    this.internalRpcHttpPort = rpcHttpPort;
  }

  @Override
  protected void doStart() throws NatInitializationException {
    LOG.info("Starting docker NAT manager.");
    try {
      ipDetector.detectAdvertisedIp().ifPresent(ipFound -> internalAdvertisedHost = ipFound);
      buildForwardedPorts();
    } catch (Exception e) {
      throw new NatInitializationException("Unable to retrieve IP from docker");
    }
  }

  @Override
  protected void doStop() {
    LOG.info("Stopping docker NAT manager.");
  }

  @Override
  protected CompletableFuture<String> retrieveExternalIPAddress() {
    return CompletableFuture.completedFuture(internalAdvertisedHost);
  }

  @Override
  public CompletableFuture<List<NatPortMapping>> getPortMappings() {
    return CompletableFuture.completedFuture(forwardedPorts);
  }

  private int getExternalPort(final int defaultValue) {
    return Optional.ofNullable(System.getenv(PORT_MAPPING_TAG + defaultValue))
        .map(Integer::valueOf)
        .orElse(defaultValue);
  }

  private void buildForwardedPorts() {
    try {
      final String internalHost = queryLocalIPAddress().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      final String advertisedHost =
          retrieveExternalIPAddress().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      forwardedPorts.add(
          new NatPortMapping(
              NatServiceType.DISCOVERY,
              NetworkProtocol.UDP,
              internalHost,
              advertisedHost,
              internalP2pPort,
              getExternalPort(internalP2pPort)));
      forwardedPorts.add(
          new NatPortMapping(
              NatServiceType.RLPX,
              NetworkProtocol.TCP,
              internalHost,
              advertisedHost,
              internalP2pPort,
              getExternalPort(internalP2pPort)));
      forwardedPorts.add(
          new NatPortMapping(
              NatServiceType.JSON_RPC,
              NetworkProtocol.TCP,
              internalHost,
              advertisedHost,
              internalRpcHttpPort,
              getExternalPort(internalRpcHttpPort)));
    } catch (Exception e) {
      LOG.warn("Failed to create forwarded port list", e);
    }
  }
}
