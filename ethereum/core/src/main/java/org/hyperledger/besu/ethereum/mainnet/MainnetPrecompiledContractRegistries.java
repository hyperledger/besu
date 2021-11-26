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
package org.hyperledger.besu.ethereum.mainnet;

import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.PLUGIN_PRIVACY;
import static org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts.populateForBLS12;
import static org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts.populateForByzantium;
import static org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts.populateForFrontier;
import static org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts.populateForIstanbul;

import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.FlexiblePrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.PrivacyPluginPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.PrivacyPrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

/** Provides the various precompiled contracts used on mainnet hard forks. */
public abstract class MainnetPrecompiledContractRegistries {

  private MainnetPrecompiledContractRegistries() {}

  public static PrecompileContractRegistry frontier(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForFrontier(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  public static PrecompileContractRegistry byzantium(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForByzantium(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  public static PrecompileContractRegistry istanbul(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForIstanbul(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  public static PrecompileContractRegistry bls12(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForBLS12(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  static void appendPrivacy(
      final PrecompileContractRegistry registry,
      final PrecompiledContractConfiguration precompiledContractConfiguration) {

    if (!precompiledContractConfiguration.getPrivacyParameters().isEnabled()) {
      return;
    }

    if (precompiledContractConfiguration.getPrivacyParameters().isPrivacyPluginEnabled()) {
      registry.put(
          PLUGIN_PRIVACY,
          new PrivacyPluginPrecompiledContract(
              precompiledContractConfiguration.getGasCalculator(),
              precompiledContractConfiguration.getPrivacyParameters()));
    } else if (precompiledContractConfiguration
        .getPrivacyParameters()
        .isFlexiblePrivacyGroupsEnabled()) {
      registry.put(
          FLEXIBLE_PRIVACY,
          new FlexiblePrivacyPrecompiledContract(
              precompiledContractConfiguration.getGasCalculator(),
              precompiledContractConfiguration.getPrivacyParameters()));
    } else {
      registry.put(
          DEFAULT_PRIVACY,
          new PrivacyPrecompiledContract(
              precompiledContractConfiguration.getGasCalculator(),
              precompiledContractConfiguration.getPrivacyParameters(),
              "Privacy"));
    }
  }
}
