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
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import com.google.common.base.Suppliers;
import io.vertx.core.Vertx;
import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.cli.options.stable.NodePrivateKeyFileOption;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.KeyPairSecurityModule;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.p2p.network.DefaultP2PNetwork;
import org.hyperledger.besu.ethereum.p2p.network.NetworkRunner;
import org.hyperledger.besu.ethereum.p2p.network.NoopP2PNetwork;
import org.hyperledger.besu.ethereum.p2p.network.ProtocolManager;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.TLSConfiguration;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.SecurityModuleService;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.services.SecurityModuleServiceImpl;
import org.hyperledger.besu.util.number.Fraction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_SECURITY_MODULE;


public class NetworkRunnerConfiguration {


    @Value("${p2p.enabled:true}")
    private boolean p2pEnabled;

    @Value("${security-module-name:localfile}")
    private String securityModuleName;

    @Value("${p2p.listen-interface:0.0.0.0}")
    private String p2pListenInterface;

    @Value("${p2p.listen-port:30303}")
    private int p2pListenPort;
    @Value("${p2p.host:localhost}")
    private String p2pAdvertisedHost;
    @Value("${p2p.max-peers:25}")
    private int maxPeers;

    @Value("${p2p.min-peers:0}")
    private int minPeers;


    @Value("${identity-string:}")
    private String identityString;

    @Value("${limit-remote-wire-connections-enabled:false}")
    private boolean limitRemoteWireConnectionsEnabled;
    private float fractionRemoteConnectionsAllowed;
    @Value("${p2p.max-remote-wire-connections-percentage}")
    private int maxRemoteConnectionsPercentage;

    @Value("${p2p.random-peer-priority-enabled:false}")
    private boolean randomPeerPriority;

    @Value("${node-private-key:#{null}}")
    private String nodePrivateKey;

    @Bean
    public NetworkRunner networkRunner(List<SubProtocol> subProtocols, List<ProtocolManager> protocolManagers, MetricsSystem metricsSystem,
                                       NetworkRunner.NetworkBuilder activeNetwork, NetworkRunner.NetworkBuilder inactiveNetwork) {

        return NetworkRunner.builder()
                .protocolManagers(protocolManagers)
                .subProtocols(subProtocols)
                .network(p2pEnabled ? activeNetwork : inactiveNetwork)
                .metricsSystem(metricsSystem)
                .build();
    }

    @Bean
    public List<SubProtocol> subProtocols() {
        return List.of(EthProtocol.get());
    }

    @Bean
    private NetworkRunner.NetworkBuilder activeNetwork(Vertx vertx,
                                                       NetworkingConfiguration networkingConfiguration,
                                                       PeerPermissions peerPermissions,
                                                       MetricsSystem metricsSystem,
                                                       NatService natService,
                                                       MutableBlockchain blockchain,
                                                       Optional<TLSConfiguration> p2pTLSConfiguration,
                                                       GenesisConfigOptions getGenesisConfigOptions,
                                                       NodeKey nodeKey,
                                                       StorageProvider storageProvider) {
        return caps ->
                DefaultP2PNetwork.builder()
                        .vertx(vertx)
                        .nodeKey(nodeKey)
                        .config(networkingConfiguration)
                        .peerPermissions(peerPermissions)
                        .metricsSystem(metricsSystem)
                        .supportedCapabilities(caps)
                        .natService(natService)
                        .randomPeerPriority(randomPeerPriority)
                        .p2pTLSConfiguration(p2pTLSConfiguration)
                        .blockchain(blockchain)
                        .forks(getGenesisConfigOptions.getForks())
                        .storageProvider(storageProvider)
                        .build();
    }


    @Bean
    private NetworkingConfiguration networkingConfiguration(RlpxConfiguration rlpxConfiguration, DiscoveryConfiguration discoveryConfiguration) {
        final NetworkingConfiguration networkingConfiguration = NetworkingConfiguration.create();
        networkingConfiguration.setRlpx(rlpxConfiguration).setDiscovery(discoveryConfiguration);
        return networkingConfiguration;
    }

    @Bean
    public DiscoveryConfiguration discoveryConfiguration() {
        return
                DiscoveryConfiguration.create()
                        .setBindHost(p2pListenInterface)
                        .setBindPort(p2pListenPort)
                        .setAdvertisedHost(p2pAdvertisedHost);
    }

    @Bean
    public RlpxConfiguration rlpxConfiguration(SubProtocolConfiguration subProtocolConfiguration) {
        return
                RlpxConfiguration.create()
                        .setBindHost(p2pListenInterface)
                        .setBindPort(p2pListenPort)
                        .setPeerUpperBound(maxPeers)
                        .setPeerLowerBound(minPeers)
                        .setSupportedProtocols(subProtocolConfiguration.getSubProtocols())
                        .setClientId(BesuInfo.nodeName(Optional.of(identityString)))
                        .setLimitRemoteWireConnectionsEnabled(limitRemoteWireConnectionsEnabled)
                        .setFractionRemoteWireConnectionsAllowed(Fraction.fromPercentage(maxRemoteConnectionsPercentage)
                                .getValue());
    }

    @Bean
    public SubProtocolConfiguration subProtocolConfiguration(EthProtocolManager ethProtocolManager, Optional<SnapProtocolManager> maybeSnapProtocolManager) {
        final SubProtocolConfiguration subProtocolConfiguration =
                new SubProtocolConfiguration().withSubProtocol(EthProtocol.get(), ethProtocolManager);
        maybeSnapProtocolManager.ifPresent(
                snapProtocolManager -> {
                    subProtocolConfiguration.withSubProtocol(SnapProtocol.get(), snapProtocolManager);
                });
        return subProtocolConfiguration;
    }

    @Bean
    public NodeKey nodeKey(SecurityModule securityModule) {
        return new NodeKey(securityModule);
    }

    @Bean
    public SecurityModule securityModule(SecurityModuleService securityModuleService) {
        return securityModuleService
                .getByName(securityModuleName)
                .orElseThrow(() -> new RuntimeException("Security Module not found: " + securityModuleName))
                .get();
    }

    @Bean
    public SecurityModuleService securityModuleService(SecurityModule defaultSecurityModule) {
        final SecurityModuleServiceImpl securityModuleService = new SecurityModuleServiceImpl();
        securityModuleService.register(
                DEFAULT_SECURITY_MODULE, Suppliers.memoize(() -> defaultSecurityModule));
        return securityModuleService;
    }

    @Bean
    public SecurityModule defaultSecurityModule(BesuConfiguration besuConfiguration) {
        return new KeyPairSecurityModule(loadKeyPair(nodePrivateKeyFile(),besuConfiguration.getDataPath()));
    }

    private File nodePrivateKeyFile() {
        if (nodePrivateKey == null) {
            return null;
        }
        return new File(nodePrivateKey);
    }

    public KeyPair loadKeyPair(final File nodePrivateKeyFile, Path dataPath) {
        return KeyPairUtil.loadKeyPair(Optional.ofNullable(nodePrivateKeyFile)
                .orElseGet(() -> KeyPairUtil.getDefaultKeyFile(dataPath)));
    }

    @Bean
    public NetworkRunner.NetworkBuilder inactiveNetwork() {
        return caps -> new NoopP2PNetwork();
    }


    @Bean
    NetworkingConfiguration networkingConfiguration(){
        return NetworkingConfiguration.create();
    }

}
