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
package org.hyperledger.besu.cli.config;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256R1;
import org.hyperledger.besu.evm.precompile.AltBN128PairingPrecompiledContract;
import org.hyperledger.besu.evm.precompile.BLS12PairingPrecompiledContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Encapsulates the native library requirements of given networks. */
public interface NativeRequirement {

  /**
   * Record type to encapsulate the result of native library loading
   *
   * @param present boolean indicating library loading present or failure.
   * @param libname string indicating the required library name.
   * @param errorMessage Optional error message suitable to log.
   */
  record NativeRequirementResult(Boolean present, String libname, Optional<String> errorMessage) {}

  /** Ethereum mainnet-like performance requirements: */
  Supplier<List<NativeRequirementResult>> MAINNET =
      () -> {
        List<NativeRequirementResult> requirements = new ArrayList<>();
        var secp256k1 = new SECP256K1();
        requirements.add(
            new NativeRequirementResult(
                secp256k1.maybeEnableNative(),
                "secp256k1",
                secp256k1.maybeEnableNative()
                    ? Optional.empty()
                    : Optional.of("secp256k1: Native secp256k1 failed to load")));

        var secp256r1 = new SECP256R1();
        requirements.add(
            new NativeRequirementResult(
                secp256r1.maybeEnableNative(),
                "secp256r1",
                secp256r1.maybeEnableNative()
                    ? Optional.empty()
                    : Optional.of("secp256r1: Native secp256r1 failed to load")));

        requirements.add(
            new NativeRequirementResult(
                AltBN128PairingPrecompiledContract.isNative(),
                "alt_bn128",
                AltBN128PairingPrecompiledContract.isNative()
                    ? Optional.empty()
                    : Optional.of("alt_bn128: EC native library failed to load")));

        requirements.add(
            new NativeRequirementResult(
                BLS12PairingPrecompiledContract.isAvailable(),
                "bls12-381",
                BLS12PairingPrecompiledContract.isAvailable()
                    ? Optional.empty()
                    : Optional.of("bls12-381: EC native library failed to load")));

        return requirements;
      };

  /**
   * Retrieves the native library requirements for a specified network.
   *
   * <p>This method checks whether the given network requires native libraries, and returns the
   * corresponding list of requirements if applicable. If the network does not require native
   * libraries, an empty list is returned.
   *
   * @param networkName the name of the network for which native requirements are being retrieved.
   * @return a list of {@link NativeRequirementResult} representing the native library requirements
   *     for the specified network, or an empty list if no requirements exist.
   */
  static List<NativeRequirementResult> getNativeRequirements(final NetworkName networkName) {
    // Check if the network requires native support.
    // Return Mainnet-like requirements or an empty list for networks without native support.
    // Can be extended to handle unique requirements for specific networks in the future.
    return networkName.hasNativeRequirements() ? MAINNET.get() : Collections.emptyList();
  }
}
