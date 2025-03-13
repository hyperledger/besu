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

import org.hyperledger.besu.evm.precompile.AltBN128PairingPrecompiledContract;
import org.hyperledger.besu.evm.precompile.BLS12PairingPrecompiledContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import ethereum.ckzg4844.CKZG4844JNI;

/**
 * Record type to encapsulate the result of native library loading
 *
 * @param success boolean indicating library loading success or failure.
 * @param libname string indicating the required library name.
 * @param errorMessage Optional error message suitable to log.
 */
public record NativeRequirement(Boolean success, String libname, Optional<String> errorMessage) {

  public static final Supplier<List<NativeRequirement>> MAINNET =
      () -> {
        List<NativeRequirement> requirements = new ArrayList<>();
        try {
          CKZG4844JNI.loadNativeLibrary();
          requirements.add(new NativeRequirement(true, "ckzg4844jni", Optional.empty()));
        } catch (Exception ex) {
          requirements.add(
              new NativeRequirement(
                  false,
                  "ckzg4844jni",
                  Optional.of(
                      "C-KZG-4844: library failed to load with exception: " + ex.getMessage())));
        }

        requirements.add(
            new NativeRequirement(
                AltBN128PairingPrecompiledContract.isNative(),
                "alt_bn128",
                AltBN128PairingPrecompiledContract.isNative()
                    ? Optional.empty()
                    : Optional.of("alt_bn128: EC native library failed to load")));

        requirements.add(
            new NativeRequirement(
                BLS12PairingPrecompiledContract.isAvailable(),
                "bls12-381",
                BLS12PairingPrecompiledContract.isAvailable()
                    ? Optional.empty()
                    : Optional.of("BLS12-381: EC native library failed to load")));

        return requirements;
      };
}
