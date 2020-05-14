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
package org.hyperledger.besu.nat.manual;

import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.core.AbstractNatManager;
import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class describes the behaviour of the Manual NAT manager. Manual Nat manager add the ability
 * to explicitly configure the external IP and Ports to broadcast without regards to NAT or other
 * considerations.
 */
public class ManualNatManager extends AbstractNatManager {
  private static final Logger LOG = LogManager.getLogger();

  private final String advertisedHost;
  private final int p2pPort;
  private final int rpcHttpPort;
  private final List<NatPortMapping> forwardedPorts;

  public ManualNatManager(final String advertisedHost, final int p2pPort, final int rpcHttpPort) {
    super(NatMethod.MANUAL);
    this.advertisedHost = advertisedHost;
    this.p2pPort = p2pPort;
    this.rpcHttpPort = rpcHttpPort;
    this.forwardedPorts = buildForwardedPorts();
  }

  private List<NatPortMapping> buildForwardedPorts() {
    try {
      final String internalHost = queryLocalIPAddress().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return Arrays.asList(
          new NatPortMapping(
              NatServiceType.DISCOVERY,
              NetworkProtocol.UDP,
              internalHost,
              advertisedHost,
              p2pPort,
              p2pPort),
          new NatPortMapping(
              NatServiceType.RLPX,
              NetworkProtocol.TCP,
              internalHost,
              advertisedHost,
              p2pPort,
              p2pPort),
          new NatPortMapping(
              NatServiceType.JSON_RPC,
              NetworkProtocol.TCP,
              internalHost,
              advertisedHost,
              rpcHttpPort,
              rpcHttpPort));
    } catch (Exception e) {
      LOG.warn("Failed to create forwarded port list", e);
    }
    return Collections.emptyList();
  }

  @Override
  protected void doStart() {
    LOG.info("Starting Manual NatManager");
  }

  @Override
  protected void doStop() {
    LOG.info("Stopping Manual NatManager");
  }

  @Override
  protected CompletableFuture<String> retrieveExternalIPAddress() {
    return CompletableFuture.completedFuture(advertisedHost);
  }

  @Override
  public CompletableFuture<List<NatPortMapping>> getPortMappings() {
    return CompletableFuture.completedFuture(forwardedPorts);
  }
}
