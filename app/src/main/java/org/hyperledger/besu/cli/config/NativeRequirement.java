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
import org.hyperledger.besu.evm.precompile.AltBN128PairingPrecompiledContract;
import org.hyperledger.besu.evm.precompile.BLS12PairingPrecompiledContract;

import java.util.ArrayList;
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
}
