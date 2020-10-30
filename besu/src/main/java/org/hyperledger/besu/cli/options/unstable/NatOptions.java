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
package org.hyperledger.besu.cli.options.unstable;

import static org.hyperledger.besu.nat.kubernetes.KubernetesNatManager.DEFAULT_BESU_SERVICE_NAME_FILTER;

import picocli.CommandLine;

public class NatOptions {

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      hidden = true,
      names = {"--Xnat-kube-service-name"},
      description =
          "Specify the name of the service that will be used by the nat manager in Kubernetes. (default: ${DEFAULT-VALUE})")
  private String natManagerServiceName = DEFAULT_BESU_SERVICE_NAME_FILTER;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xnat-method-fallback-enabled"},
      description =
          "Enable fallback to NONE for the nat manager in case of failure. If False BESU will exit on failure. (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Boolean natMethodFallbackEnabled = true;

  public static NatOptions create() {
    return new NatOptions();
  }

  public String getNatManagerServiceName() {
    return natManagerServiceName;
  }

  public Boolean getNatMethodFallbackEnabled() {
    return natMethodFallbackEnabled;
  }
}
