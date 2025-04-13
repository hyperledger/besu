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
package org.hyperledger.besu.cli.converter

import org.hyperledger.besu.ethereum.core.plugins.PluginInfo
import picocli.CommandLine
import java.util.stream.Stream

/**
 * Converts a comma-separated string into a list of [PluginInfo] objects. This converter is
 * intended for use with PicoCLI to process command line arguments that specify plugin information.
 */
class PluginInfoConverter
/** Default Constructor.  */
    : CommandLine.ITypeConverter<List<PluginInfo>> {
    /**
     * Converts a comma-separated string into a list of [PluginInfo].
     *
     * @param value The comma-separated string representing plugin names.
     * @return A list of [PluginInfo] objects created from the provided string.
     */
    override fun convert(value: String?): List<PluginInfo> {
        if (value == null || value.isBlank()) {
            return listOf()
        }
        return Stream.of(*value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            .map { obj: String -> obj.trim { it <= ' ' } }.map { pluginName: String -> this.toPluginInfo(pluginName) }
            .toList()
    }

    /**
     * Creates a [PluginInfo] object from a plugin name.
     *
     * @param pluginName The name of the plugin.
     * @return A [PluginInfo] object representing the plugin.
     */
    private fun toPluginInfo(pluginName: String): PluginInfo {
        return PluginInfo(pluginName)
    }
}
