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
package org.hyperledger.besu.springmain.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mining")
public class MiningOptionsProperties {
    private String stratumExtranonce;
    private int remoteSealersLimit;
    private long remoteSealersTimeToLive;
    private long powJobTimeToLive;
    private int maxOmmersDepth;
    private long posBlockCreationMaxTime;

    public String getStratumExtranonce() {
        return stratumExtranonce;
    }

    public void setStratumExtranonce(final String stratumExtranonce) {
        this.stratumExtranonce = stratumExtranonce;
    }

    public int getRemoteSealersLimit() {
        return remoteSealersLimit;
    }

    public void setRemoteSealersLimit(final int remoteSealersLimit) {
        this.remoteSealersLimit = remoteSealersLimit;
    }

    public long getRemoteSealersTimeToLive() {
        return remoteSealersTimeToLive;
    }

    public void setRemoteSealersTimeToLive(final long remoteSealersTimeToLive) {
        this.remoteSealersTimeToLive = remoteSealersTimeToLive;
    }

    public long getPowJobTimeToLive() {
        return powJobTimeToLive;
    }

    public void setPowJobTimeToLive(final long powJobTimeToLive) {
        this.powJobTimeToLive = powJobTimeToLive;
    }

    public int getMaxOmmersDepth() {
        return maxOmmersDepth;
    }

    public void setMaxOmmersDepth(final int maxOmmersDepth) {
        this.maxOmmersDepth = maxOmmersDepth;
    }

    public long getPosBlockCreationMaxTime() {
        return posBlockCreationMaxTime;
    }

    public void setPosBlockCreationMaxTime(final long posBlockCreationMaxTime) {
        this.posBlockCreationMaxTime = posBlockCreationMaxTime;
    }
}
