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
package org.hyperledger.besu.services

import org.hyperledger.besu.plugin.services.PicoCLIOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

/** The Pico cli options service implementation to specify plugins.  */
class PicoCLIOptionsImpl
/**
 * Instantiates a new Pico cli options.
 *
 * @param commandLine the command line
 */(private val commandLine: CommandLine) : PicoCLIOptions {
    override fun addPicoCLIOptions(namespace: String, optionObject: Any) {
        val pluginPrefix = "--plugin-$namespace-"
        val unstablePrefix = "--Xplugin-$namespace-"
        val mixin = CommandLine.Model.CommandSpec.forAnnotatedObject(optionObject)
        var badOptionName = false

        for (optionSpec in mixin.options()) {
            for (optionName in optionSpec.names()) {
                if (!optionName.startsWith(pluginPrefix) && !optionName.startsWith(unstablePrefix)) {
                    badOptionName = true
                    LOG.error(
                        "Plugin option {} did not have the expected prefix of {}", optionName, pluginPrefix
                    )
                }
            }
        }
        if (badOptionName) {
            throw RuntimeException("Error loading CLI options")
        } else {
            commandLine.commandSpec.addMixin("Plugin $namespace", mixin)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(PicoCLIOptionsImpl::class.java)
    }
}
