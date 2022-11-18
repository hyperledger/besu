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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "metrics")
public class MetricsProperties {
    private boolean enabled;
    private boolean pushEnabled;
    private String host;
    private int port;
    private String protocol;
    private String pushHost;
    private int pushPort;
    private int pushInterval;
    private String[] allowList;
    private String prometheusJob;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public void setPushEnabled(final boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
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

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(final String protocol) {
        this.protocol = protocol;
    }

    public String getPushHost() {
        return pushHost;
    }

    public void setPushHost(final String pushHost) {
        this.pushHost = pushHost;
    }

    public int getPushPort() {
        return pushPort;
    }

    public void setPushPort(final int pushPort) {
        this.pushPort = pushPort;
    }

    public int getPushInterval() {
        return pushInterval;
    }

    public void setPushInterval(final int pushInterval) {
        this.pushInterval = pushInterval;
    }

    public String[] getAllowList() {
        return allowList;
    }

    public void setAllowList(final String[] allowList) {
        this.allowList = allowList;
    }

    public String getPrometheusJob() {
        return prometheusJob;
    }

    public void setPrometheusJob(final String prometheusJob) {
        this.prometheusJob = prometheusJob;
    }
}
