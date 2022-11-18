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

@ConfigurationProperties(prefix = "txpool")

public class TXPoolProperties {
    private int txPoolMaxSize;
    private int pendingTxRetentionPeriod;
    private int priceBump;

    public int getTxPoolMaxSize() {
        return txPoolMaxSize;
    }

    public void setTxPoolMaxSize(final int txPoolMaxSize) {
        this.txPoolMaxSize = txPoolMaxSize;
    }

    public int getPendingTxRetentionPeriod() {
        return pendingTxRetentionPeriod;
    }

    public void setPendingTxRetentionPeriod(final int pendingTxRetentionPeriod) {
        this.pendingTxRetentionPeriod = pendingTxRetentionPeriod;
    }

    public int getPriceBump() {
        return priceBump;
    }

    public void setPriceBump(final int priceBump) {
        this.priceBump = priceBump;
    }
}
