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
package org.hyperledger.besu.cli.util

import picocli.CommandLine

/**
 * The custom default value provider for Besu CLI options which uses multiple default value
 * providers such as environment variables or TOML file.
 */
class CascadingDefaultProvider
/**
 * Instantiates a new Cascading default provider.
 *
 * @param defaultValueProviders List of default value providers
 */(private val defaultValueProviders: List<CommandLine.IDefaultValueProvider>) :
    CommandLine.IDefaultValueProvider {
    @Throws(Exception::class)
    override fun defaultValue(argSpec: CommandLine.Model.ArgSpec): String? {
        for (provider in defaultValueProviders) {
            val defaultValue = provider.defaultValue(argSpec)
            if (defaultValue != null) {
                return defaultValue
            }
        }
        return null
    }
}
