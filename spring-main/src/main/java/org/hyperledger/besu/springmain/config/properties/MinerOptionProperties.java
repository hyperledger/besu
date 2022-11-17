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


import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class MinerOptionProperties {

    private String coinbase;
    private Bytes extraData;
    private boolean miningEnabled;
    private boolean stratumMiningEnabled;
    private String stratumNetworkInterface;
    private int stratumPort;

    public Address translateCoinbase() {
        return Address.fromHexString(coinbase);
    }

    public String getCoinbase() {
        return coinbase;
    }

    public void setCoinbase(final String coinbase) {
        this.coinbase = coinbase;
    }

    public Bytes getExtraData() {
        return extraData;
    }

    public void setExtraData(final Bytes extraData) {
        this.extraData = extraData;
    }

    public boolean isMiningEnabled() {
        return miningEnabled;
    }

    public void setMiningEnabled(final boolean miningEnabled) {
        this.miningEnabled = miningEnabled;
    }

    public boolean isStratumMiningEnabled() {
        return stratumMiningEnabled;
    }

    public void setStratumMiningEnabled(final boolean stratumMiningEnabled) {
        this.stratumMiningEnabled = stratumMiningEnabled;
    }

    public String getStratumNetworkInterface() {
        return stratumNetworkInterface;
    }

    public void setStratumNetworkInterface(final String stratumNetworkInterface) {
        this.stratumNetworkInterface = stratumNetworkInterface;
    }

    public int getStratumPort() {
        return stratumPort;
    }

    public void setStratumPort(final int stratumPort) {
        this.stratumPort = stratumPort;
    }
}