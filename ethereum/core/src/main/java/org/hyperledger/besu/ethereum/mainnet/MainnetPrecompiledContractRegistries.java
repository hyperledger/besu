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
import org.hyperledger.besu.ethereum.mainnet.precompiles.BLS12G1AddPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BLS12G1MulPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BLS12G1MultiExpPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BLS12G2AddPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BLS12G2MulPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BLS12G2MultiExpPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BLS12MapFp2ToG2PrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BLS12MapFpToG1PrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BLS12PairingPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.BigIntegerModularExponentiationPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.ECRECPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.IDPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.RIPEMD160PrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.SHA256PrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.OnChainPrivacyPrecompiledContract;
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

  private static void populateForIstanbul(
      final PrecompileContractRegistry registry,
      final GasCalculator gasCalculator,
      final int accountVersion) {
    populateForByzantium(registry, gasCalculator, accountVersion);
    registry.put(
        Address.ALTBN128_ADD,
        Account.DEFAULT_VERSION,
        AltBN128AddPrecompiledContract.istanbul(gasCalculator));
    registry.put(
        Address.ALTBN128_MUL,
        Account.DEFAULT_VERSION,
        AltBN128MulPrecompiledContract.istanbul(gasCalculator));
    registry.put(
        Address.ALTBN128_PAIRING,
        Account.DEFAULT_VERSION,
        AltBN128PairingPrecompiledContract.istanbul(gasCalculator));
    registry.put(
        Address.BLAKE2B_F_COMPRESSION,
        Account.DEFAULT_VERSION,
        new BLAKE2BFPrecompileContract(gasCalculator));
  }

  public static PrecompileContractRegistry istanbul(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForIstanbul(
        registry, precompiledContractConfiguration.getGasCalculator(), Account.DEFAULT_VERSION);
    return registry;
  }

  private static void populateForBerlin(
      final PrecompileContractRegistry registry,
      final GasCalculator gasCalculator,
      final int accountVersion) {
    populateForIstanbul(registry, gasCalculator, accountVersion);
    registry.put(Address.BLS12_G1ADD, Account.DEFAULT_VERSION, new BLS12G1AddPrecompiledContract());
    registry.put(Address.BLS12_G1MUL, Account.DEFAULT_VERSION, new BLS12G1MulPrecompiledContract());
    registry.put(
        Address.BLS12_G1MULTIEXP,
        Account.DEFAULT_VERSION,
        new BLS12G1MultiExpPrecompiledContract());
    registry.put(Address.BLS12_G2ADD, Account.DEFAULT_VERSION, new BLS12G2AddPrecompiledContract());
    registry.put(Address.BLS12_G2MUL, Account.DEFAULT_VERSION, new BLS12G2MulPrecompiledContract());
    registry.put(
        Address.BLS12_G2MULTIEXP,
        Account.DEFAULT_VERSION,
        new BLS12G2MultiExpPrecompiledContract());
    registry.put(
        Address.BLS12_PAIRING, Account.DEFAULT_VERSION, new BLS12PairingPrecompiledContract());
    registry.put(
        Address.BLS12_MAP_FP_TO_G1,
        Account.DEFAULT_VERSION,
        new BLS12MapFpToG1PrecompiledContract());
    registry.put(
        Address.BLS12_MAP_FP2_TO_G2,
        Account.DEFAULT_VERSION,
        new BLS12MapFp2ToG2PrecompiledContract());
  }

  public static PrecompileContractRegistry berlin(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForBerlin(
        registry, precompiledContractConfiguration.getGasCalculator(), Account.DEFAULT_VERSION);
    return registry;
  }

  static void appendPrivacy(
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
    registry.put(
        Address.ONCHAIN_PRIVACY,
        accountVersion,
        new OnChainPrivacyPrecompiledContract(
            precompiledContractConfiguration.getGasCalculator(),
            precompiledContractConfiguration.getPrivacyParameters()));
  }
}
