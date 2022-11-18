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

@ConfigurationProperties(prefix = "p2p")

public class P2PProperties {

    private boolean enabled;
    private boolean peerDiscoveryEnabled;
    private String[] bootNodes;
    private String host;
    private String p2pInterface;
    private int port;
    private int minPeers;
    private int maxPeers;
    private boolean limitRemoteWireConnections;
    private int maxRemoteConnectionsPercentage;
    private String discoveryDnsUrl;
    private boolean randomPeerPriority;


//    void setBannedNodeIds(final List<String> values) {
//        try {
//            bannedNodeIds =
//                    values.stream()
//                            .filter(value -> !value.isEmpty())
//                            .map(EnodeURLImpl::parseNodeId)
//                            .collect(Collectors.toList());
//        } catch (final IllegalArgumentException e) {
//            throw new ParameterException(
//                    new CommandLine(this),
//                    "Invalid ids supplied to '--banned-node-ids'. " + e.getMessage());
//        }
//    }


    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPeerDiscoveryEnabled() {
        return peerDiscoveryEnabled;
    }

    public void setPeerDiscoveryEnabled(final boolean peerDiscoveryEnabled) {
        this.peerDiscoveryEnabled = peerDiscoveryEnabled;
    }

    public String[] getBootNodes() {
        return bootNodes;
    }

    public void setBootNodes(final String[] bootNodes) {
        this.bootNodes = bootNodes;
    }

    public String getHost() {
        return host;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public String getP2pInterface() {
        return p2pInterface;
    }

    public void setP2pInterface(final String p2pInterface) {
        this.p2pInterface = p2pInterface;
    }

    public int getPort() {
        return port;
    }

    public void setPort(final int port) {
        this.port = port;
    }

    public int getMinPeers() {
        return minPeers;
    }

    public void setMinPeers(final int minPeers) {
        this.minPeers = minPeers;
    }

    public int getMaxPeers() {
        return maxPeers;
    }

    public void setMaxPeers(final int maxPeers) {
        this.maxPeers = maxPeers;
    }

    public boolean isLimitRemoteWireConnections() {
        return limitRemoteWireConnections;
    }

    public void setLimitRemoteWireConnections(final boolean limitRemoteWireConnections) {
        this.limitRemoteWireConnections = limitRemoteWireConnections;
    }

    public int getMaxRemoteConnectionsPercentage() {
        return maxRemoteConnectionsPercentage;
    }

    public void setMaxRemoteConnectionsPercentage(final int maxRemoteConnectionsPercentage) {
        this.maxRemoteConnectionsPercentage = maxRemoteConnectionsPercentage;
    }

    public String getDiscoveryDnsUrl() {
        return discoveryDnsUrl;
    }

    public void setDiscoveryDnsUrl(final String discoveryDnsUrl) {
        this.discoveryDnsUrl = discoveryDnsUrl;
    }

    public boolean isRandomPeerPriority() {
        return randomPeerPriority;
    }

    public void setRandomPeerPriority(final boolean randomPeerPriority) {
        this.randomPeerPriority = randomPeerPriority;
    }
}
