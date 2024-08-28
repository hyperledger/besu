/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm;

import static org.hyperledger.besu.evm.MainnetEVMs.registerIstanbulOperations;

import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.operation.Push0Operation;

import java.math.BigInteger;

/** Provides EVMs supporting the appropriate operations for ETC network upgrades. */
public class ClassicEVMs {
  /** Default constructor. */
  private ClassicEVMs() {}

  /**
   * spiral evm.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @param evmConfiguration the evm configuration
   * @return the evm
   */
  public static EVM spiral(
      final GasCalculator gasCalculator,
      final BigInteger chainId,
      final EvmConfiguration evmConfiguration) {
    return new EVM(
        spiralOperations(gasCalculator, chainId),
        gasCalculator,
        evmConfiguration,
        EvmSpecVersion.SHANGHAI);
  }

  /**
   * spiral operations' registry.
   *
   * @param gasCalculator the gas calculator
   * @param chainId the chain id
   * @return the operation registry
   */
  public static OperationRegistry spiralOperations(
      final GasCalculator gasCalculator, final BigInteger chainId) {
    OperationRegistry registry = new OperationRegistry();
    registerIstanbulOperations(registry, gasCalculator, chainId);
    registry.put(new Push0Operation(gasCalculator));
    return registry;
  }
}
