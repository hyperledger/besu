/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.springmain.config;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import com.google.common.base.Strings;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.tls.TlsConfiguration;
import org.hyperledger.besu.springmain.config.properties.EngineRpcProperties;
import org.hyperledger.besu.springmain.config.properties.JsonRPCHttpProperties;
import org.hyperledger.besu.springmain.config.properties.P2PProperties;
import org.springframework.context.annotation.Bean;

public class JsonRPCConfiguration {

    @Bean
    public JsonRpcConfiguration jsonRpcConfiguration(JsonRPCHttpProperties jsonRPCHttpProperties, P2PProperties p2PProperties, EngineRpcProperties engineRpcProperties) {
//        checkRpcTlsClientAuthOptionsDependencies();
//        checkRpcTlsOptionsDependencies();
//        checkRpcHttpOptionsDependencies();

//        if (jsonRPCHttpOptionGroup.isRpcHttpAuthenticationEnabled) {
//            CommandLineUtils.checkOptionDependencies(
//                    logger,
//                    commandLine,
//                    "--rpc-http-authentication-public-key-file",
//                    jsonRPCHttpOptionGroup.rpcHttpAuthenticationPublicKeyFile == null,
//                    asList("--rpc-http-authentication-jwt-algorithm"));
//        }
//
//        if (jsonRPCHttpOptionGroup.isRpcHttpAuthenticationEnabled
//                && rpcHttpAuthenticationCredentialsFile() == null
//                && jsonRPCHttpOptionGroup.rpcHttpAuthenticationPublicKeyFile == null) {
//            throw new ParameterException(
//                    commandLine,
//                    "Unable to authenticate JSON-RPC HTTP endpoint without a supplied credentials file or authentication public key file");
//        }

        final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
        jsonRpcConfiguration.setEnabled(jsonRPCHttpProperties.isEnabled());
        jsonRpcConfiguration.setHost(
                Strings.isNullOrEmpty(jsonRPCHttpProperties.getHost())
                        ? p2PProperties.getHost()
                        : jsonRPCHttpProperties.getHost());
        jsonRpcConfiguration.setPort(jsonRPCHttpProperties.getPort());
        jsonRpcConfiguration.setMaxActiveConnections(jsonRPCHttpProperties.getMaxConnections());
        jsonRpcConfiguration.setCorsAllowedDomains(Arrays.asList(jsonRPCHttpProperties.getCorsAllowedOrigins()));
        jsonRpcConfiguration.setRpcApis(Arrays.stream(jsonRPCHttpProperties.getApis()).distinct()
                .collect(Collectors.toList()));
        jsonRpcConfiguration.setNoAuthRpcApis(Arrays.stream(jsonRPCHttpProperties.getNoAuthApis())
                .distinct()
                .collect(Collectors.toList()));
        jsonRpcConfiguration.setHostsAllowlist(Arrays.stream(engineRpcProperties.getAllowList()).distinct()
                .collect(Collectors.toList()));
        jsonRpcConfiguration.setAuthenticationEnabled(
                jsonRPCHttpProperties.isAuthenticationEnabled());
        jsonRpcConfiguration.setAuthenticationCredentialsFile(jsonRPCHttpProperties.getAuthenticationCredentialsFile());
        jsonRpcConfiguration.setAuthenticationPublicKeyFile(
                jsonRPCHttpProperties.getHttpAuthenticationPublicKeyFile());
        jsonRpcConfiguration.setAuthenticationAlgorithm(
                jsonRPCHttpProperties.getAuthenticationAlgorithm());
        jsonRpcConfiguration.setTlsConfiguration(rpcHttpTlsConfiguration());
        jsonRpcConfiguration.setHttpTimeoutSec(jsonRPCHttpProperties.getTimeoutSeconds());
        return jsonRpcConfiguration;
    }

    @Bean
    private Optional<TlsConfiguration> rpcHttpTlsConfiguration() {
        return Optional.empty();
    }



    @Bean
    public InetAddress autoDiscoverDefaultIP() {
        return InetAddress.getLoopbackAddress();
    }

}
