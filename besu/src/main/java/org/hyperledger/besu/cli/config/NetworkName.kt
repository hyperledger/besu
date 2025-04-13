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
package org.hyperledger.besu.cli.config

import org.apache.commons.lang3.StringUtils
import org.hyperledger.besu.cli.config.NativeRequirement.NativeRequirementResult
import java.math.BigInteger
import java.util.*
import java.util.function.Supplier

/** The enum Network name.  */
enum class NetworkName // no deprecations planned
@JvmOverloads constructor(
  /**
     * Gets genesis file.
     *
     * @return the genesis file
     */
  @JvmField val genesisFile: String,
  /**
     * Gets network id.
     *
     * @return the network id
     */
  @JvmField val networkId: BigInteger,
  private val canSnapSync: Boolean = true,
  private val nativeRequirements: Supplier<List<NativeRequirementResult>> = Supplier { emptyList() }
) {
    /** Mainnet network name.  */
    MAINNET("/mainnet.json", BigInteger.valueOf(1), true, NativeRequirement.MAINNET),

    /** Sepolia network name.  */
    SEPOLIA("/sepolia.json", BigInteger.valueOf(11155111), true, NativeRequirement.MAINNET),

    /** Holešky network name.  */
    HOLESKY("/holesky.json", BigInteger.valueOf(17000), true, NativeRequirement.MAINNET),

    /** Hoodi network name.  */
    HOODI("/hoodi.json", BigInteger.valueOf(560048), true, NativeRequirement.MAINNET),

    /**
     * EPHEMERY network name. The actual networkId used is calculated based on this default value and
     * the current time. https://ephemery.dev/
     */
    EPHEMERY("/ephemery.json", BigInteger.valueOf(39438135), true, NativeRequirement.MAINNET),

    /** LUKSO mainnet network name.  */
    LUKSO("/lukso.json", BigInteger.valueOf(42)),

    /** Dev network name.  */
    DEV("/dev.json", BigInteger.valueOf(2018), false),

    /** Future EIPs network name.  */
    FUTURE_EIPS("/future.json", BigInteger.valueOf(2022), false),

    /** Experimental EIPs network name.  */
    EXPERIMENTAL_EIPS("/experimental.json", BigInteger.valueOf(2023), false),

    /** Classic network name.  */
    CLASSIC("/classic.json", BigInteger.valueOf(1)),

    /** Mordor network name.  */
    MORDOR("/mordor.json", BigInteger.valueOf(7));

    private val deprecationDate: String? = null

    /**
     * Can SNAP sync boolean.
     *
     * @return the boolean
     */
    fun canSnapSync(): Boolean {
        return canSnapSync
    }

    /**
     * Normalize string.
     *
     * @return the string
     */
    fun normalize(): String {
        return StringUtils.capitalize(name.lowercase())
    }

    val isDeprecated: Boolean
        /**
         * Is deprecated boolean.
         *
         * @return the boolean
         */
        get() = deprecationDate != null

    /**
     * Gets deprecation date.
     *
     * @return the deprecation date
     */
    fun getDeprecationDate(): Optional<String> {
        return Optional.ofNullable(deprecationDate)
    }

    /**
     * Gets native requirements for this network.
     *
     * @return result of native library requirements defined for this network, as a list.
     */
    fun getNativeRequirements(): List<NativeRequirementResult> {
        return nativeRequirements.get()
    }
}
