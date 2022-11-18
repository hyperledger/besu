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

package org.hyperledger.besu.springmain.config.properties;

import java.io.File;
import java.nio.file.Path;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.JwtAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "json-rpc")
public class JsonRPCHttpProperties {
    private File httpAuthenticationPublicKeyFile;
    private boolean enabled;
    private String host;
    private int port;
    private int maxConnections;
    private String[] corsAllowedOrigins;
    private String[] apis;
    private String[] noAuthApis;
    private boolean authenticationEnabled;
    private String authenticationCredentialsFile;
    private JwtAlgorithm authenticationAlgorithm;
    private boolean tlsEnabled;
    private Path tlsKeyStoreFile;
    private Path tlsKEyStorePasswordFile;
    private boolean tlsClientAuthEnabled;
    private Path tlsKnownClientsFile;
    private boolean tlsCAClientsEnabled;
    private String[] tlsProtocols;
    private String[] tlsCipherSuites;
    private int timeoutSeconds;


    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(final int port) {
        this.port = port;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(final int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public String[] getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(final String[] corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public String[] getApis() {
        return apis;
    }

    public void setApis(final String[] apis) {
        this.apis = apis;
    }

    public String[] getNoAuthApis() {
        return noAuthApis;
    }

    public void setNoAuthApis(final String[] noAuthApis) {
        this.noAuthApis = noAuthApis;
    }

    public boolean isAuthenticationEnabled() {
        return authenticationEnabled;
    }

    public void setAuthenticationEnabled(final boolean authenticationEnabled) {
        this.authenticationEnabled = authenticationEnabled;
    }

    public String getAuthenticationCredentialsFile() {
        return authenticationCredentialsFile;
    }

    public void setAuthenticationCredentialsFile(final String authenticationCredentialsFile) {
        this.authenticationCredentialsFile = authenticationCredentialsFile;
    }

    public File getHttpAuthenticationPublicKeyFile() {
        return httpAuthenticationPublicKeyFile;
    }

    public void setHttpAuthenticationPublicKeyFile(final File httpAuthenticationPublicKeyFile) {
        this.httpAuthenticationPublicKeyFile = httpAuthenticationPublicKeyFile;
    }

    public JwtAlgorithm getAuthenticationAlgorithm() {
        return authenticationAlgorithm;
    }

    public void setAuthenticationAlgorithm(final JwtAlgorithm authenticationAlgorithm) {
        this.authenticationAlgorithm = authenticationAlgorithm;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public void setTlsEnabled(final boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    public Path getTlsKeyStoreFile() {
        return tlsKeyStoreFile;
    }

    public void setTlsKeyStoreFile(final Path tlsKeyStoreFile) {
        this.tlsKeyStoreFile = tlsKeyStoreFile;
    }

    public Path getTlsKEyStorePasswordFile() {
        return tlsKEyStorePasswordFile;
    }

    public void setTlsKEyStorePasswordFile(final Path tlsKEyStorePasswordFile) {
        this.tlsKEyStorePasswordFile = tlsKEyStorePasswordFile;
    }

    public boolean isTlsClientAuthEnabled() {
        return tlsClientAuthEnabled;
    }

    public void setTlsClientAuthEnabled(final boolean tlsClientAuthEnabled) {
        this.tlsClientAuthEnabled = tlsClientAuthEnabled;
    }

    public Path getTlsKnownClientsFile() {
        return tlsKnownClientsFile;
    }

    public void setTlsKnownClientsFile(final Path tlsKnownClientsFile) {
        this.tlsKnownClientsFile = tlsKnownClientsFile;
    }

    public Boolean getRpcHttpTlsCAClientsEnabled() {
        return tlsCAClientsEnabled;
    }

    public void setRpcHttpTlsCAClientsEnabled(final Boolean rpcHttpTlsCAClientsEnabled) {
        tlsCAClientsEnabled = rpcHttpTlsCAClientsEnabled;
    }

    public String[] getTlsProtocols() {
        return tlsProtocols;
    }

    public void setTlsProtocols(final String[] tlsProtocols) {
        this.tlsProtocols = tlsProtocols;
    }

    public String[] getTlsCipherSuites() {
        return tlsCipherSuites;
    }

    public void setTlsCipherSuites(final String[] tlsCipherSuites) {
        this.tlsCipherSuites = tlsCipherSuites;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(final int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
