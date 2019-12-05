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

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.mainnet.precompiles.AltBN128AddPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.AltBN128MulPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.AltBN128PairingPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BLAKE2BFPrecompileContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BigIntegerModularExponentiationPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.ECRECPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.IDPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.RIPEMD160PrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.SHA256PrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.PrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.vm.GasCalculator;

/** Provides the various precompiled contracts used on mainnet hard forks. */
public abstract class MainnetPrecompiledContractRegistries {

  private MainnetPrecompiledContractRegistries() {}

  private static void populateForFrontier(
      final PrecompileContractRegistry registry,
      final GasCalculator gasCalculator,
      final int accountVersion) {
    registry.put(Address.ECREC, accountVersion, new ECRECPrecompiledContract(gasCalculator));
    registry.put(Address.SHA256, accountVersion, new SHA256PrecompiledContract(gasCalculator));
    registry.put(
        Address.RIPEMD160, accountVersion, new RIPEMD160PrecompiledContract(gasCalculator));
    registry.put(Address.ID, accountVersion, new IDPrecompiledContract(gasCalculator));
  }

  public static PrecompileContractRegistry frontier(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForFrontier(
        registry, precompiledContractConfiguration.getGasCalculator(), Account.DEFAULT_VERSION);
    return registry;
  }

  private static void populateForByzantium(
      final PrecompileContractRegistry registry,
      final GasCalculator gasCalculator,
      final int accountVersion) {
    populateForFrontier(registry, gasCalculator, accountVersion);
    registry.put(
        Address.MODEXP,
        accountVersion,
        new BigIntegerModularExponentiationPrecompiledContract(gasCalculator));
    registry.put(
        Address.ALTBN128_ADD,
        accountVersion,
        AltBN128AddPrecompiledContract.byzantium(gasCalculator));
    registry.put(
        Address.ALTBN128_MUL,
        accountVersion,
        AltBN128MulPrecompiledContract.byzantium(gasCalculator));
    registry.put(
        Address.ALTBN128_PAIRING,
        accountVersion,
        AltBN128PairingPrecompiledContract.byzantium(gasCalculator));
  }

  public static PrecompileContractRegistry byzantium(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForByzantium(
        registry, precompiledContractConfiguration.getGasCalculator(), Account.DEFAULT_VERSION);
    return registry;
  }

  public static PrecompileContractRegistry istanbul(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForByzantium(
        registry, precompiledContractConfiguration.getGasCalculator(), Account.DEFAULT_VERSION);
    registry.put(
        Address.ALTBN128_ADD,
        Account.DEFAULT_VERSION,
        AltBN128AddPrecompiledContract.istanbul(
            precompiledContractConfiguration.getGasCalculator()));
    registry.put(
        Address.ALTBN128_MUL,
        Account.DEFAULT_VERSION,
        AltBN128MulPrecompiledContract.istanbul(
            precompiledContractConfiguration.getGasCalculator()));
    registry.put(
        Address.ALTBN128_PAIRING,
        Account.DEFAULT_VERSION,
        AltBN128PairingPrecompiledContract.istanbul(
            precompiledContractConfiguration.getGasCalculator()));
    registry.put(
        Address.BLAKE2B_F_COMPRESSION,
        Account.DEFAULT_VERSION,
        new BLAKE2BFPrecompileContract(precompiledContractConfiguration.getGasCalculator()));

    return registry;
  }

  static PrecompileContractRegistry appendPrivacy(
      final PrecompileContractRegistry registry,
      final PrecompiledContractConfiguration precompiledContractConfiguration,
      final int accountVersion) {
    final Address address =
        Address.privacyPrecompiled(
            precompiledContractConfiguration.getPrivacyParameters().getPrivacyAddress());
    registry.put(
        address,
        accountVersion,
        new PrivacyPrecompiledContract(
            precompiledContractConfiguration.getGasCalculator(),
            precompiledContractConfiguration.getPrivacyParameters()));
    return registry;
  }
}
