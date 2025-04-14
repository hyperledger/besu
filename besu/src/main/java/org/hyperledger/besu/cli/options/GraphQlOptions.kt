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
package org.hyperledger.besu.cli.options

import com.google.common.base.Strings
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.custom.CorsAllowedOriginsProperty
import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration
import org.slf4j.Logger
import picocli.CommandLine

/** Handles configuration options for the GraphQL HTTP service in Besu.  */ // TODO: implement CLIOptions<GraphQLConfiguration>
class GraphQlOptions
/** Default constructor  */
{
    /**
     * Checks if GraphQL over HTTP is enabled.
     *
     * @return true if enabled, false otherwise
     */
    @JvmField
    @CommandLine.Option(
        names = ["--graphql-http-enabled"],
        description = ["Set to start the GraphQL HTTP service (default: \${DEFAULT-VALUE})"]
    )
    var isGraphQLHttpEnabled: Boolean = false

    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--graphql-http-host"],
        paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
        description = ["Host for GraphQL HTTP to listen on (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    private var graphQLHttpHost: String? = null

    /**
     * Returns the port for GraphQL over HTTP.
     *
     * @return The port number
     */
    @JvmField
    @CommandLine.Option(
        names = ["--graphql-http-port"],
        paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
        description = ["Port for GraphQL HTTP to listen on (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var graphQLHttpPort: Int = GraphQLConfiguration.DEFAULT_GRAPHQL_HTTP_PORT

    @CommandLine.Option(
        names = ["--graphql-http-cors-origins"],
        description = ["Comma separated origin domain URLs for CORS validation (default: none)"]
    )
    private var graphQLHttpCorsAllowedOrigins = CorsAllowedOriginsProperty()

    @CommandLine.Option(names = ["--graphql-tls-enabled"], description = ["Enable TLS for GraphQL HTTP service"])
    private var graphqlTlsEnabled = false

    @CommandLine.Option(
        names = ["--graphql-tls-keystore-file"],
        description = ["Path to the TLS keystore file for GraphQL HTTP service"]
    )
    private var graphqlTlsKeystoreFile: String? = null

    @CommandLine.Option(
        names = ["--graphql-tls-keystore-password-file"],
        description = ["Path to the file containing the password for the TLS keystore"]
    )
    private var graphqlTlsKeystorePasswordFile: String? = null

    @CommandLine.Option(names = ["--graphql-mtls-enabled"], description = ["Enable mTLS for GraphQL HTTP service"])
    private var graphqlMtlsEnabled = false

    @CommandLine.Option(
        names = ["--graphql-tls-truststore-file"],
        description = ["Path to the TLS truststore file for GraphQL HTTP service"]
    )
    private var graphqlTlsTruststoreFile: String? = null

    @CommandLine.Option(
        names = ["--graphql-tls-truststore-password-file"],
        description = ["Path to the file containing the password for the TLS truststore"]
    )
    private var graphqlTlsTruststorePasswordFile: String? = null

    /**
     * Validates the GraphQL HTTP options.
     *
     * @param logger Logger instance
     * @param commandLine CommandLine instance
     */
    fun validate(logger: Logger?, commandLine: CommandLine?) {
        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--graphql-http-enabled",
            !isGraphQLHttpEnabled,
            mutableListOf(
                "--graphql-http-cors-origins",
                "--graphql-http-host",
                "--graphql-http-port",
                "--graphql-tls-enabled"
            )
        )

        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--graphql-tls-enabled",
            !graphqlTlsEnabled,
            mutableListOf(
                "--graphql-tls-keystore-file",
                "--graphql-tls-keystore-password-file",
                "--graphql-mtls-enabled"
            )
        )

        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--graphql-mtls-enabled",
            !graphqlMtlsEnabled,
            mutableListOf("--graphql-tls-truststore-file", "--graphql-tls-truststore-password-file")
        )
    }

    /**
     * Creates a GraphQLConfiguration based on the provided options.
     *
     * @param hostsAllowlist List of hosts allowed
     * @param defaultHostAddress Default host address
     * @param timoutSec Timeout in seconds
     * @return A GraphQLConfiguration instance
     */
    fun graphQLConfiguration(
        hostsAllowlist: List<String?>?, defaultHostAddress: String?, timoutSec: Long
    ): GraphQLConfiguration {
        val graphQLConfiguration = GraphQLConfiguration.createDefault()
        graphQLConfiguration.isEnabled = isGraphQLHttpEnabled
        graphQLConfiguration.host =
            if (Strings.isNullOrEmpty(graphQLHttpHost)) defaultHostAddress else graphQLHttpHost
        graphQLConfiguration.port = graphQLHttpPort
        graphQLConfiguration.setHostsAllowlist(hostsAllowlist)
        graphQLConfiguration.setCorsAllowedDomains(graphQLHttpCorsAllowedOrigins)
        graphQLConfiguration.httpTimeoutSec = timoutSec
        graphQLConfiguration.isTlsEnabled = graphqlTlsEnabled
        graphQLConfiguration.tlsKeyStorePath = graphqlTlsKeystoreFile
        graphQLConfiguration.setTlsKeyStorePasswordFile(graphqlTlsKeystorePasswordFile)
        graphQLConfiguration.isMtlsEnabled = graphqlMtlsEnabled
        graphQLConfiguration.tlsTrustStorePath = graphqlTlsTruststoreFile
        graphQLConfiguration.setTlsTrustStorePasswordFile(graphqlTlsTruststorePasswordFile)

        return graphQLConfiguration
    }
}
