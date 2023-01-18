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
package org.hyperledger.besu.nat.kubernetes.service;

import org.hyperledger.besu.nat.core.IpDetector;
import org.hyperledger.besu.nat.core.exception.NatInitializationException;

import java.net.InetAddress;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1Service;

/** The Load balancer based detector. */
public class LoadBalancerBasedDetector implements IpDetector {

  private final V1Service v1Service;

  /**
   * Instantiates a new Load balancer based detector.
   *
   * @param v1Service the v 1 service
   */
  public LoadBalancerBasedDetector(final V1Service v1Service) {
    this.v1Service = v1Service;
  }

  @Override
  public Optional<String> detectAdvertisedIp() throws Exception {
    final V1LoadBalancerIngress v1LoadBalancerIngress =
        v1Service.getStatus().getLoadBalancer().getIngress().stream()
            .filter(
                v1LoadBalancerIngress1 ->
                    v1LoadBalancerIngress1.getHostname() != null
                        || v1LoadBalancerIngress1.getIp() != null)
            .findFirst()
            .orElseThrow(() -> new NatInitializationException("Ingress not found"));
    if (v1LoadBalancerIngress.getHostname() != null) {
      return Optional.ofNullable(
          InetAddress.getByName(v1LoadBalancerIngress.getHostname()).getHostAddress());
    } else {
      return Optional.ofNullable(v1LoadBalancerIngress.getIp());
    }
  }
}
