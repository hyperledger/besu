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

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "engine-rpc")
public class EngineRpcProperties {
    private boolean authDisabled;
    private boolean overrideEngineRpcEnabled;
    private int port;
    private Path engineJwtKeyFile;
    private boolean deprecatedIsEngineAuthEnabled;
    private String[] allowList;


    public boolean isOverrideEngineRpcEnabled() {
        return overrideEngineRpcEnabled;
    }

    public void setOverrideEngineRpcEnabled(final boolean overrideEngineRpcEnabled) {
        this.overrideEngineRpcEnabled = overrideEngineRpcEnabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(final int port) {
        this.port = port;
    }

    public Path getEngineJwtKeyFile() {
        return engineJwtKeyFile;
    }

    public void setEngineJwtKeyFile(final Path engineJwtKeyFile) {
        this.engineJwtKeyFile = engineJwtKeyFile;
    }

    public boolean isDeprecatedIsEngineAuthEnabled() {
        return deprecatedIsEngineAuthEnabled;
    }

    public void setDeprecatedIsEngineAuthEnabled(final boolean deprecatedIsEngineAuthEnabled) {
        this.deprecatedIsEngineAuthEnabled = deprecatedIsEngineAuthEnabled;
    }


    public boolean isAuthDisabled() {
        return authDisabled;
    }

    public void setAuthDisabled(final boolean authDisabled) {
        this.authDisabled = authDisabled;
    }

    public String[] getAllowList() {
        return allowList;
    }

    public void setAllowList(final String[] allowList) {
        this.allowList = allowList;
    }
}
