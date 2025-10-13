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
package org.hyperledger.besu.evm.precompile;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/** Provides the various precompiled contracts used on mainnet hard forks. */
public interface MainnetPrecompiledContracts {

  /**
   * Frontier precompile contract registry.
   *
   * @param gasCalculator the gas calculator
   * @return the precompile contract registry
   */
  static PrecompileContractRegistry frontier(final GasCalculator gasCalculator) {
    PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
    populateForFrontier(precompileContractRegistry, gasCalculator);
    return precompileContractRegistry;
  }

  /**
   * Populate registry for frontier.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   */
  static void populateForFrontier(
      final PrecompileContractRegistry registry, final GasCalculator gasCalculator) {
    registry.put(Address.ECREC, new ECRECPrecompiledContract(gasCalculator));
    registry.put(Address.SHA256, new SHA256PrecompiledContract(gasCalculator));
    registry.put(Address.RIPEMD160, new RIPEMD160PrecompiledContract(gasCalculator));
    registry.put(Address.ID, new IDPrecompiledContract(gasCalculator));
  }

  /**
   * Homestead precompile contract registry.
   *
   * @param gasCalculator the gas calculator
   * @return the precompile contract registry
   */
  static PrecompileContractRegistry homestead(final GasCalculator gasCalculator) {
    return frontier(gasCalculator);
  }

  /**
   * Byzantium precompile contract registry.
   *
   * @param gasCalculator the gas calculator
   * @return the precompile contract registry
   */
  static PrecompileContractRegistry byzantium(final GasCalculator gasCalculator) {
    PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
    populateForByzantium(precompileContractRegistry, gasCalculator);
    return precompileContractRegistry;
  }

  /**
   * Populate registry for byzantium.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   */
  static void populateForByzantium(
      final PrecompileContractRegistry registry, final GasCalculator gasCalculator) {
    populateForFrontier(registry, gasCalculator);
    registry.put(
        Address.MODEXP, new BigIntegerModularExponentiationPrecompiledContract(gasCalculator));
    registry.put(Address.ALTBN128_ADD, AltBN128AddPrecompiledContract.byzantium(gasCalculator));
    registry.put(Address.ALTBN128_MUL, AltBN128MulPrecompiledContract.byzantium(gasCalculator));
    registry.put(
        Address.ALTBN128_PAIRING, AltBN128PairingPrecompiledContract.byzantium(gasCalculator));
  }

  /**
   * Istanbul precompile contract registry.
   *
   * @param gasCalculator the gas calculator
   * @return the precompile contract registry
   */
  static PrecompileContractRegistry istanbul(final GasCalculator gasCalculator) {
    PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
    populateForIstanbul(precompileContractRegistry, gasCalculator);
    return precompileContractRegistry;
  }

  /**
   * Populate registry for istanbul.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   */
  static void populateForIstanbul(
      final PrecompileContractRegistry registry, final GasCalculator gasCalculator) {
    populateForByzantium(registry, gasCalculator);
    registry.put(Address.ALTBN128_ADD, AltBN128AddPrecompiledContract.istanbul(gasCalculator));
    registry.put(Address.ALTBN128_MUL, AltBN128MulPrecompiledContract.istanbul(gasCalculator));
    registry.put(
        Address.ALTBN128_PAIRING, AltBN128PairingPrecompiledContract.istanbul(gasCalculator));
    registry.put(Address.BLAKE2B_F_COMPRESSION, new BLAKE2BFPrecompileContract(gasCalculator));
  }

  /**
   * Cancun precompile contract registry.
   *
   * @param gasCalculator the gas calculator
   * @return the precompile contract registry
   */
  static PrecompileContractRegistry cancun(final GasCalculator gasCalculator) {
    PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
    populateForCancun(precompileContractRegistry, gasCalculator);
    return precompileContractRegistry;
  }

  /**
   * Populate registry for Cancun.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   */
  static void populateForCancun(
      final PrecompileContractRegistry registry, final GasCalculator gasCalculator) {
    populateForIstanbul(registry, gasCalculator);

    // EIP-4844 - shard blob transactions
    registry.put(Address.KZG_POINT_EVAL, new KZGPointEvalPrecompiledContract());
  }

  /**
   * Prague precompile contract registry.
   *
   * @param gasCalculator the gas calculator
   * @return the precompile contract registry
   */
  static PrecompileContractRegistry prague(final GasCalculator gasCalculator) {
    PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
    populateForPrague(precompileContractRegistry, gasCalculator);
    return precompileContractRegistry;
  }

  /**
   * Populate registry for Prague.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   */
  static void populateForPrague(
      final PrecompileContractRegistry registry, final GasCalculator gasCalculator) {
    populateForCancun(registry, gasCalculator);

    // EIP-2537 - BLS12-381 curve operations
    registry.put(Address.BLS12_G1ADD, new BLS12G1AddPrecompiledContract());
    registry.put(Address.BLS12_G1MULTIEXP, new BLS12G1MultiExpPrecompiledContract());
    registry.put(Address.BLS12_G2ADD, new BLS12G2AddPrecompiledContract());
    registry.put(Address.BLS12_G2MULTIEXP, new BLS12G2MultiExpPrecompiledContract());
    registry.put(Address.BLS12_PAIRING, new BLS12PairingPrecompiledContract());
    registry.put(Address.BLS12_MAP_FP_TO_G1, new BLS12MapFpToG1PrecompiledContract());
    registry.put(Address.BLS12_MAP_FP2_TO_G2, new BLS12MapFp2ToG2PrecompiledContract());
  }

  /**
   * FutureEIPs precompile contract registry.
   *
   * @param gasCalculator the gas calculator
   * @return the precompile contract registry
   */
  static PrecompileContractRegistry futureEIPs(final GasCalculator gasCalculator) {
    PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
    populateForFutureEIPs(precompileContractRegistry, gasCalculator);
    return precompileContractRegistry;
  }

  /**
   * Populate registry for Future EIPs.
   *
   * @param registry the registry
   * @param gasCalculator the gas calculator
   */
  static void populateForFutureEIPs(
      final PrecompileContractRegistry registry, final GasCalculator gasCalculator) {
    populateForCancun(registry, gasCalculator);
  }
}
