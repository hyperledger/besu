/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm.processor;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

import java.util.Map;

/**
 * A message call processor designed specifically for simulation purposes that allows for overriding
 * precompile addresses.
 */
public class OverriddenPrecompilesMessageCallProcessor extends MessageCallProcessor {

  /**
   * Instantiates a new Modifiable precompiles message call processor for simulation.
   *
   * @param originalProcessor the original processor
   * @param precompileOverrides the address overrides
   */
  public OverriddenPrecompilesMessageCallProcessor(
      final MessageCallProcessor originalProcessor,
      final Map<Address, Address> precompileOverrides) {
    super(
        originalProcessor.evm,
        createRegistryWithPrecompileOverrides(originalProcessor.precompiles, precompileOverrides));
  }

  /**
   * Creates a new PrecompileContractRegistry with the specified address overrides.
   *
   * @param originalRegistry the original precompile contract registry
   * @param precompileOverrides the address overrides
   * @return a new PrecompileContractRegistry with the overrides applied
   */
  private static PrecompileContractRegistry createRegistryWithPrecompileOverrides(
      final PrecompileContractRegistry originalRegistry,
      final Map<Address, Address> precompileOverrides) {
    PrecompileContractRegistry newRegistry = new PrecompileContractRegistry();
    for (Address originalAddress : originalRegistry.getPrecompileAddresses()) {
      Address effectiveAddress = precompileOverrides.getOrDefault(originalAddress, originalAddress);
      newRegistry.put(effectiveAddress, originalRegistry.get(originalAddress));
    }
    return newRegistry;
  }
}
