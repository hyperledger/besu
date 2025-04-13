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
package org.hyperledger.besu.cli.config

import org.hyperledger.besu.crypto.SECP256K1
import org.hyperledger.besu.evm.precompile.AltBN128PairingPrecompiledContract
import org.hyperledger.besu.evm.precompile.BLS12PairingPrecompiledContract
import java.util.*
import java.util.function.Supplier

/** Encapsulates the native library requirements of given networks.  */
interface NativeRequirement {
    /**
     * Record type to encapsulate the result of native library loading
     *
     * @param present boolean indicating library loading present or failure.
     * @param libname string indicating the required library name.
     * @param errorMessage Optional error message suitable to log.
     */
    @JvmRecord
    data class NativeRequirementResult(@JvmField val present: Boolean, @JvmField val libname: String, @JvmField val errorMessage: Optional<String>)

    companion object {
        /** Ethereum mainnet-like performance requirements:  */
        @JvmField
        val MAINNET: Supplier<List<NativeRequirementResult>> =
            Supplier {
                val requirements: MutableList<NativeRequirementResult> = ArrayList()
                val secp256k1 = SECP256K1()
                requirements.add(
                    NativeRequirementResult(
                        secp256k1.maybeEnableNative(),
                        "secp256k1",
                        if (secp256k1.maybeEnableNative())
                            Optional.empty()
                        else
                            Optional.of("secp256k1: Native secp256k1 failed to load")
                    )
                )

                requirements.add(
                    NativeRequirementResult(
                        AltBN128PairingPrecompiledContract.isNative(),
                        "alt_bn128",
                        if (AltBN128PairingPrecompiledContract.isNative())
                            Optional.empty()
                        else
                            Optional.of("alt_bn128: EC native library failed to load")
                    )
                )

                requirements.add(
                    NativeRequirementResult(
                        BLS12PairingPrecompiledContract.isAvailable(),
                        "bls12-381",
                        if (BLS12PairingPrecompiledContract.isAvailable())
                            Optional.empty()
                        else
                            Optional.of("bls12-381: EC native library failed to load")
                    )
                )
                requirements
            }
    }
}
