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
package org.hyperledger.besu.cli.config

import org.apache.commons.lang3.StringUtils
import java.util.*
import java.util.stream.Collectors

/**
 * Enum for profile names which are bundled. Each profile corresponds to a bundled configuration
 * file.
 */
enum class InternalProfileName
/**
 * Constructs a new ProfileName.
 *
 * @param configFile the configuration file corresponding to the profile
 */(
    /**
     * Gets the configuration file corresponding to the profile.
     *
     * @return the configuration file
     */
    @JvmField val configFile: String
) {
    /** The 'STAKER' profile  */
    STAKER("profiles/staker.toml"),

    /** The 'MINIMALIST_STAKER' profile  */
    MINIMALIST_STAKER("profiles/minimalist-staker.toml"),

    /** The 'ENTERPRISE' profile  */
    ENTERPRISE("profiles/enterprise-private.toml"),

    /** The 'PRIVATE' profile  */
    PRIVATE("profiles/enterprise-private.toml"),

    /** The 'DEV' profile.  */
    DEV("profiles/dev.toml");

    override fun toString(): String {
        return StringUtils.capitalize(name.replace("_".toRegex(), " "))
    }

    /** TODO: remove after rewriting ProfileFinder.java in Kotlin **/
    fun getConfigFile(): String {
        return configFile
    }

    companion object {
        /**
         * Returns the InternalProfileName that matches the given name, ignoring case.
         *
         * @param name The profile name
         * @return Optional InternalProfileName if found, otherwise empty
         */
        @JvmStatic
        fun valueOfIgnoreCase(name: String?): Optional<InternalProfileName> {
            return Arrays.stream(entries.toTypedArray())
                .filter { profile: InternalProfileName -> profile.name.equals(name, ignoreCase = true) }
                .findFirst()
        }

        @JvmStatic
        val internalProfileNames: Set<String>
            /**
             * Returns the set of internal profile names as lowercase.
             *
             * @return Set of internal profile names
             */
            get() = Arrays.stream(entries.toTypedArray())
                .map { obj: InternalProfileName -> obj.name }
                .map { obj: String -> obj.lowercase(Locale.getDefault()) }
                .collect(Collectors.toSet())
    }
}
