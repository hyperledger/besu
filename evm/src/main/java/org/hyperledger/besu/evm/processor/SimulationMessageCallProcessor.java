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

import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;

/**
 * A message call processor designed specifically for simulation purposes that allows for overriding
 * precompile addresses.
 */
public class SimulationMessageCallProcessor extends MessageCallProcessor {

  /**
   * Instantiates a new Modifiable precompiles message call processor for simulation.
   *
   * @param originalProcessor the original processor
   * @param precompileContractRegistryAdapter PrecompileContractRegistryProvider
   */
  public SimulationMessageCallProcessor(
      final MessageCallProcessor originalProcessor,
      final Function<PrecompileContractRegistry, PrecompileContractRegistry>
          precompileContractRegistryAdapter) {
    super(
        originalProcessor.evm,
        precompileContractRegistryAdapter.apply(originalProcessor.precompiles));
  }

  /**
   * Gets precompiles.
   *
   * @return the PrecompileContractRegistry
   */
  @VisibleForTesting
  public PrecompileContractRegistry getPrecompiles() {
    return precompiles;
  }
}
