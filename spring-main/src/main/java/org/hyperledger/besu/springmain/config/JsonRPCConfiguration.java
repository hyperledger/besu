/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.springmain.config;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import com.google.common.base.Strings;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.JwtAlgorithm;
import org.hyperledger.besu.ethereum.api.tls.TlsConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonRPCConfiguration {
    @Value("${rpc.http.enabled:false}")
    private boolean isRpcHttpEnabled;

    @Value("${rpc.http.host:}")
    private String rpcHttpHost;

    @Value("${rpc.http.port:8545}")
    private int rpcHttpPort;

    @Value("${rpc.http.max-connections:80}")
    private int rpcHttpMaxConnections;

    @Value("${rpc.http.cors.allowed-origins:}")
    private String[] rpcHttpCorsAllowedOrigins;
    @Value("${rpc.http.allow-list:}")
    private String[] allowList;

    @Value("${rpc.http.apis:ETH,NET,WEB3}")
    private String[] rpcHttpApis;

    @Value("${rpc.http.apis.no-auth:}")
    private String[] rpcHttpApiMethodsNoAuth;

    @Value("${rpc.http.authentication.enabled:false}")
    private boolean isRpcHttpAuthenticationEnabled;

    @Value("${rpc.http.authentication.credentials-file:}")
    private String rpcHttpAuthenticationCredentialsFile;

    @Value("${rpc.http.authentication.public-key-file:}")
    private String rpcHttpAuthenticationPublicKeyFile;

    @Value("${rpc.http.authentication.authentication-algorithm:RS256}")
    private String rpcHttpAuthenticationAlgorithm;

    @Value("${rpc.http.tls.enabled:false}")
    private boolean isRpcHttpTlsEnabled;

    @Value("${rpc.http.tls.key-store-file:}")
    private String rpcHttpTlsKeyStoreFile;

    @Value("${rpc.http.tls.key-store-password-file:}")
    private String rpcHttpTlsKeyStorePasswordFile;

    @Value("${rpc.http.tls.client-auth.enabled:false}")
    private boolean isRpcHttpTlsClientAuthEnabled;

    @Value("${rpc.http.tls.known-clients-file:}")
    private String rpcHttpTlsKnownClientsFile;

    @Value("${rpc.http.tls.ca-clients.enabled:false}")
    private boolean isRpcHttpTlsCAClientsEnabled;

    //httpTimeoutSec
    @Value("${rpc.http.timeout-seconds:300}")
    private int rpcHttpTimeoutSeconds;


    @Value("${rpc.http.tls.protocols:TLSv1.3,TLSv1.2}")
    private String[] rpcHttpTlsProtocols;

    @Value("${rpc.http.tls.cipher-suites:}")
    private String[] rpcHttpTlsCipherSuites;

    @Value("${p2p.enabled:false}")
    private boolean p2pEnabled = true;

    @Value("${p2p.boot-nodes:}")
    private String[] bootNodes;

    //p2pHost
    @Value("${p2p.host:localhost")
    private String p2pHost;

    //p2pInterface
    @Value("${p2p.interface:0.0.0.0}")
    private String p2pInterface;

    //p2pPort
    @Value("${p2p.port:30303}")
    private int p2pPort;

    //maxPeers
    @Value("${p2p.max-peers:25}")
    private int maxPeers;

    @Value("${p2p.limit-remote-wire-connections-enabled:false}")
    private boolean isLimitRemoteWireConnectionsEnabled;

    @Value("${p2p.max-remote-wire-connections-percentage:50}")
    private int maxRemoteConnectionsPercentage;

    @Value("${p2p.discovery-dns-url:}")
    private String discoveryDnsUrl;

    @Value("${p2p.random-peer-priority:false}")
    private boolean randomPeerPriority;


    @Bean
    public JsonRpcConfiguration jsonRpcConfiguration() {
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
        jsonRpcConfiguration.setEnabled(isRpcHttpEnabled);
        jsonRpcConfiguration.setHost(
                Strings.isNullOrEmpty(rpcHttpHost)
                        ? p2pHost
                        : rpcHttpHost);
        jsonRpcConfiguration.setPort(rpcHttpPort);
        jsonRpcConfiguration.setMaxActiveConnections(rpcHttpMaxConnections);
        jsonRpcConfiguration.setCorsAllowedDomains(Arrays.asList(rpcHttpCorsAllowedOrigins));
        jsonRpcConfiguration.setRpcApis(Arrays.stream(rpcHttpApis).distinct().collect(Collectors.toList()));
        jsonRpcConfiguration.setNoAuthRpcApis(Arrays.stream(rpcHttpApiMethodsNoAuth).distinct()
                .collect(Collectors.toList()));
        jsonRpcConfiguration.setHostsAllowlist(Arrays.stream(allowList).distinct().collect(Collectors.toList()));
        jsonRpcConfiguration.setAuthenticationEnabled(
                isRpcHttpAuthenticationEnabled);
        jsonRpcConfiguration.setAuthenticationCredentialsFile(rpcHttpAuthenticationCredentialsFile);
        jsonRpcConfiguration.setAuthenticationPublicKeyFile(
                rpcHttpAuthenticationPublicKeyFile());
        jsonRpcConfiguration.setAuthenticationAlgorithm(
                rpcHttpAuthenticationAlgorithm());
        jsonRpcConfiguration.setTlsConfiguration(rpcHttpTlsConfiguration());
        jsonRpcConfiguration.setHttpTimeoutSec(rpcHttpTimeoutSeconds);
        return jsonRpcConfiguration;
    }

    private Optional<TlsConfiguration> rpcHttpTlsConfiguration() {
        return Optional.empty();
    }

    private JwtAlgorithm rpcHttpAuthenticationAlgorithm() {
        return JwtAlgorithm.fromString(rpcHttpAuthenticationAlgorithm);
    }

    private File rpcHttpAuthenticationPublicKeyFile() {
        return rpcHttpAuthenticationPublicKeyFile == null
                ? null
                : new File(rpcHttpAuthenticationPublicKeyFile);
    }
}
