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
import org.hyperledger.besu.nat.core.NatMethodDetector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/** The Kubernetes detector. */
public class KubernetesDetector implements NatMethodDetector {

  // When a Pod runs on a Node, the kubelet adds a set of environment variables for each active
  // Service.
  // https://kubernetes.io/docs/concepts/services-networking/connect-applications-service/#environment-variables
  private static final Optional<String> KUBERNETES_SERVICE_HOST =
      Optional.ofNullable(System.getenv("KUBERNETES_SERVICE_HOST"));
  private static final Path KUBERNETES_WATERMARK_FILE = Paths.get("var/run/secrets/kubernetes.io");

  /** Default constructor */
  public KubernetesDetector() {}

  @Override
  public Optional<NatMethod> detect() {
    return KUBERNETES_SERVICE_HOST
        .map(__ -> NatMethod.KUBERNETES)
        .or(
            () ->
                Files.exists(KUBERNETES_WATERMARK_FILE)
                    ? Optional.of(NatMethod.KUBERNETES)
                    : Optional.empty());
  }
}
