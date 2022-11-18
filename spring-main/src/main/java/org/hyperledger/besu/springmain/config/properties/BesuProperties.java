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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;



@ConfigurationProperties(prefix = "besu")
public class BesuProperties {
    private long reorgLoggingThreshold;
    private boolean revertReasonEnabled;
    private String keyValueStorageName;
    private String dataPath;
    private int fastSyncMinPeerCount;
    private long targetGasLimit;
    private File nodePrivateKeyFile;
    private String securityModuleName;
    private String identityString;
    private boolean limitRemoteWireConnectionsEnabled;


    public long getReorgLoggingThreshold() {
        return reorgLoggingThreshold;
    }

    public void setReorgLoggingThreshold(final long reorgLoggingThreshold) {
        this.reorgLoggingThreshold = reorgLoggingThreshold;
    }

    public boolean isRevertReasonEnabled() {
        return revertReasonEnabled;
    }

    public void setRevertReasonEnabled(final boolean revertReasonEnabled) {
        this.revertReasonEnabled = revertReasonEnabled;
    }

    public String getKeyValueStorageName() {
        return keyValueStorageName;
    }

    public void setKeyValueStorageName(final String keyValueStorageName) {
        this.keyValueStorageName = keyValueStorageName;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(final String dataPath) {
        this.dataPath = dataPath;
    }

    public int getFastSyncMinPeerCount() {
        return fastSyncMinPeerCount;
    }

    public void setFastSyncMinPeerCount(final int fastSyncMinPeerCount) {
        this.fastSyncMinPeerCount = fastSyncMinPeerCount;
    }

    public Long getTargetGasLimit() {
        return targetGasLimit;
    }

    public void setTargetGasLimit(final Long targetGasLimit) {
        this.targetGasLimit = targetGasLimit;
    }

    public File getNodePrivateKeyFile() {
        return nodePrivateKeyFile;
    }

    public void setNodePrivateKeyFile(final File nodePrivateKeyFile) {
        this.nodePrivateKeyFile = nodePrivateKeyFile;
    }

    public String getSecurityModuleName() {

        return securityModuleName;
    }

    public void setSecurityModuleName(final String securityModuleName) {
        this.securityModuleName = securityModuleName;
    }

    public String getIdentityString() {
        return identityString;
    }

    public void setIdentityString(final String identityString) {
        this.identityString = identityString;
    }

    public boolean isLimitRemoteWireConnectionsEnabled() {
        return limitRemoteWireConnectionsEnabled;
    }

    public void setLimitRemoteWireConnectionsEnabled(final boolean limitRemoteWireConnectionsEnabled) {
        this.limitRemoteWireConnectionsEnabled = limitRemoteWireConnectionsEnabled;
    }
}
