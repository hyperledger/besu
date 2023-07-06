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
package org.hyperledger.besu.ethereum.permissioning;

import java.util.Optional;

public class PermissioningConfiguration {

  private final Optional<LocalPermissioningConfiguration> localConfig;
  private final Optional<SmartContractPermissioningConfiguration> smartContractConfig;
  private final Optional<GoQuorumPermissioningConfiguration> quorumPermissioningConfig;

  public PermissioningConfiguration(
      final Optional<LocalPermissioningConfiguration> localConfig,
      final Optional<SmartContractPermissioningConfiguration> smartContractConfig,
      final Optional<GoQuorumPermissioningConfiguration> quorumPermissioningConfig) {
    this.localConfig = localConfig;
    this.smartContractConfig = smartContractConfig;
    this.quorumPermissioningConfig = quorumPermissioningConfig;
  }

  public Optional<LocalPermissioningConfiguration> getLocalConfig() {
    return localConfig;
  }

  public Optional<SmartContractPermissioningConfiguration> getSmartContractConfig() {
    return smartContractConfig;
  }

  public Optional<GoQuorumPermissioningConfiguration> getQuorumPermissioningConfig() {
    return quorumPermissioningConfig;
  }

  public static PermissioningConfiguration createDefault() {
    return new PermissioningConfiguration(Optional.empty(), Optional.empty(), Optional.empty());
  }
}
