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
import java.util.*
import java.util.stream.Stream

/** The Environment variable default provider used in PicoCli.  */
class EnvironmentVariableDefaultProvider
/**
 * Instantiates a new Environment variable default provider.
 *
 * @param environment the environment
 */(private val environment: Map<String, String>) : CommandLine.IDefaultValueProvider {
    override fun defaultValue(argSpec: CommandLine.Model.ArgSpec): String? {
        if (argSpec.isPositional) {
            return null // skip default for positional params
        }

        return envVarNames(argSpec as CommandLine.Model.OptionSpec)
            .map { key: String -> environment[key] }
            .filter { obj: String? -> Objects.nonNull(obj) }
            .findFirst()
            .orElse(null)
    }

    private fun envVarNames(spec: CommandLine.Model.OptionSpec): Stream<String> {
        return Arrays.stream(spec.names())
            .filter { name: String -> name.startsWith("--") }  // Only long options are allowed
            .flatMap { name: String ->
                Stream.of(ENV_VAR_PREFIX, LEGACY_ENV_VAR_PREFIX)
                    .map { prefix: String -> prefix + nameToEnvVarSuffix(name) }
            }
    }

    private fun nameToEnvVarSuffix(name: String): String {
        return name.substring("--".length).replace('-', '_').uppercase()
    }

    companion object {
        private const val ENV_VAR_PREFIX = "BESU_"
        private const val LEGACY_ENV_VAR_PREFIX = "PANTHEON_"
    }
}
