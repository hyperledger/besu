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
package org.hyperledger.besu.tests.acceptance.plugins

import com.google.auto.service.AutoService
import org.hyperledger.besu.plugin.BesuPlugin
import org.hyperledger.besu.plugin.ServiceManager
import org.hyperledger.besu.plugin.services.BesuService
import org.hyperledger.besu.plugin.services.PicoCLIOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@AutoService(BesuPlugin::class)
class BadCLIOptionsPlugin : BesuPlugin {
    @CommandLine.Option(names = ["--poorly-named-option"])
    var poorlyNamedOption: String = "nothing"

    private var callbackDir: File? = null

    override fun register(context: ServiceManager) {
        LOG.info("Registering BadCliOptionsPlugin")
        callbackDir = File(System.getProperty("besu.plugins.dir", "plugins"))
        writeStatus("init")

        if (System.getProperty("TEST_BAD_CLI", "false") == "true") {
            context
                .getService<PicoCLIOptions>(PicoCLIOptions::class.java)
                .ifPresent { picoCLIOptions: BesuService? ->
                    (picoCLIOptions as PicoCLIOptions).addPicoCLIOptions(
                        "bad-cli",
                        this@BadCLIOptionsPlugin
                    )
                }
        }

        writeStatus("register")
    }

    override fun start() {
        LOG.info("Starting BadCliOptionsPlugin")
        writeStatus("start")
    }

    override fun stop() {
        LOG.info("Stopping BadCliOptionsPlugin")
        writeStatus("stop")
    }

    private fun writeStatus(status: String) {
        try {
            val callbackFile = File(callbackDir, "badCLIOptions.$status")
            if (!callbackFile.parentFile.exists()) {
                callbackFile.parentFile.mkdirs()
                callbackFile.parentFile.deleteOnExit()
            }
            Files.write(callbackFile.toPath(), status.toByteArray(StandardCharsets.UTF_8))
            callbackFile.deleteOnExit()
            LOG.info("Write status $status")
        } catch (ioe: IOException) {
            throw RuntimeException(ioe)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(BadCLIOptionsPlugin::class.java)
    }
}
