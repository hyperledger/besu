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

import org.hyperledger.besu.plugin.services.PluginVersionsProvider
import org.hyperledger.besu.util.BesuVersionUtils
import picocli.CommandLine
import java.util.stream.Stream

/** The Version provider used by PicoCli to report Besu version on Cli.  */
class VersionProvider
/**
 * Instantiates a new Version provider.
 *
 * @param pluginVersionsProvider the plugin versions provider
 */(private val pluginVersionsProvider: PluginVersionsProvider) : CommandLine.IVersionProvider {
    override fun getVersion(): Array<String> {
        // the PluginVersionsProvider has registered plugins and their versions by this time.
        return Stream.concat<String>(
            Stream.of<String>(BesuVersionUtils.version()),
            pluginVersionsProvider.pluginVersions.stream()
        )
            .toArray<String> { arrayOf() }
    }
}
