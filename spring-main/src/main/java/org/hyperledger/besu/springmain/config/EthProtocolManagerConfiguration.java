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

package org.hyperledger.besu.springmain.config;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import com.google.common.io.Resources;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.cli.options.unstable.DnsOptions;
import org.hyperledger.besu.cli.options.unstable.EthProtocolOptions;
import org.hyperledger.besu.cli.options.unstable.SynchronizerOptions;
import org.hyperledger.besu.cli.options.unstable.TransactionPoolOptions;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.PostMergeContext;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ConsensusContextFactory;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.MergePeerFilter;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolFactory;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.springmain.config.properties.BesuProperties;
import org.hyperledger.besu.springmain.config.properties.GenesisProperties;
import org.hyperledger.besu.springmain.config.properties.MinerOptionProperties;
import org.hyperledger.besu.springmain.config.properties.MiningOptionsProperties;
import org.hyperledger.besu.springmain.config.properties.P2PProperties;
import org.hyperledger.besu.springmain.config.properties.TXPoolProperties;
import org.hyperledger.besu.util.number.Percentage;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO;
import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_MIN_TRANSACTION_GAS_PRICE;
import static org.hyperledger.besu.cli.config.NetworkName.MAINNET;

public class EthProtocolManagerConfiguration {


    @Bean
    public EthProtocolManager ethProtocolManager(MutableBlockchain blockchain,
                                                 WorldStateArchive worldStateArchive,
                                                 EthNetworkConfig ethNetworkConfig,
                                                 SynchronizerConfiguration synchronizerConfiguration,
                                                 TransactionPool transactionPool,
                                                 EthProtocolConfiguration ethereumWireProtocolConfiguration,
                                                 EthPeers ethPeers,
                                                 final EthContext ethContext,
                                                 final EthMessages ethMessages,
                                                 final EthScheduler scheduler,
                                                 final List<PeerValidator> peerValidators,
                                                 final Optional<MergePeerFilter> mergePeerFilter,
                                                 GenesisConfigFile genesisConfig) {
        return new EthProtocolManager(
                blockchain,
                ethNetworkConfig.getNetworkId(),
                worldStateArchive,
                transactionPool,
                ethereumWireProtocolConfiguration,
                ethPeers,
                ethMessages,
                ethContext,
                peerValidators,
                mergePeerFilter,
                synchronizerConfiguration,
                scheduler,
                genesisConfig.getForks());
    }

    @Bean
    EthNetworkConfig ethNetworkConfig(NetworkName network,
                                      GenesisConfigFile genesisConfigFile,
                                      GenesisConfigOptions genesisConfigOptions,
                                      EnodeDnsConfiguration enodeDnsConfiguration,
                                      GenesisProperties genesisProperties) throws IOException {
        final EthNetworkConfig.Builder builder =
                new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(network));

        if (genesisFile(genesisProperties.getGenesisFilename()) != null) {

            builder.setGenesisConfig(Resources.toString(genesisFile(genesisProperties.getGenesisFilename()).toURI()
                    .toURL(), UTF_8));

            if (genesisProperties.getNetworkId() == null) {
                // If no chain id is found in the genesis, use mainnet network id
                builder.setNetworkId(
                        genesisConfigFile
                                .getConfigOptions(genesisConfigOverrides())
                                .getChainId()
                                .orElse(EthNetworkConfig.getNetworkConfig(MAINNET).getNetworkId()));
            }

//            if (p2PDiscoveryOptionGroup.bootNodes == null) {
//                builder.setBootNodes(new ArrayList<>());
//            }
            builder.setDnsDiscoveryUrl(null);
        }

/*        if (p2PDiscoveryOptionGroup.discoveryDnsUrl != null) {
            builder.setDnsDiscoveryUrl(p2PDiscoveryOptionGroup.discoveryDnsUrl);
        } else if (genesisConfigOptions != null) {*/
        final Optional<String> discoveryDnsUrlFromGenesis =
                genesisConfigOptions.getDiscoveryOptions().getDiscoveryDnsUrl();
        discoveryDnsUrlFromGenesis.ifPresent(builder::setDnsDiscoveryUrl);
//        }

        if (genesisProperties.getNetworkId() != null) {
            builder.setNetworkId(genesisProperties.getNetworkId());
        }

        List<EnodeURL> listBootNodes = null;
//        if (p2PDiscoveryOptionGroup.bootNodes != null) {
//            try {
//                listBootNodes = buildEnodes(p2PDiscoveryOptionGroup.bootNodes, getEnodeDnsConfiguration());
//            } catch (final IllegalArgumentException e) {
//                throw new ParameterException(commandLine, e.getMessage());
//            }
//        } else if (genesisConfigOptions != null) {
        final Optional<List<String>> bootNodesFromGenesis =
                genesisConfigOptions.getDiscoveryOptions().getBootNodes();
        if (bootNodesFromGenesis.isPresent()) {
            listBootNodes = buildEnodes(bootNodesFromGenesis.get(), enodeDnsConfiguration);
        }
//        }
        if (listBootNodes != null) {
/*            if (!p2PDiscoveryOptionGroup.peerDiscoveryEnabled) {
                logger.warn("Discovery disabled: bootnodes will be ignored.");
            }*/
            DiscoveryConfiguration.assertValidBootnodes(listBootNodes);
            builder.setBootNodes(listBootNodes);
        }
        return builder.build();

    }

    private Map<String, String> genesisConfigOverrides() {
        return Map.of();
    }

    private File genesisFile(final String genesisFilename) {
        if (genesisFilename == null) {
            return null;
        }
        return new File(genesisFilename);
    }

    @Bean
    NetworkName network() {
        return NetworkName.MAINNET;
    }


    private List<EnodeURL> buildEnodes(
            final List<String> bootNodes, final EnodeDnsConfiguration enodeDnsConfiguration) {
        return bootNodes.stream()
                .filter(bootNode -> !bootNode.isEmpty())
                .map(bootNode -> EnodeURLImpl.fromString(bootNode, enodeDnsConfiguration))
                .collect(Collectors.toList());
    }

    @Bean
    EnodeDnsConfiguration enodeDnsConfiguration() {
        return DnsOptions.create().toDomainObject();
    }

    @Bean
    SynchronizerConfiguration synchronizerConfiguration(SyncMode syncMode, BesuProperties besuProperties) {
        return SynchronizerOptions.create().toDomainObject()
                .syncMode(syncMode)
                .fastSyncMinimumPeerCount(besuProperties.getFastSyncMinPeerCount())
                .build();
    }

    @Bean
    SyncMode syncMode() {
        return SyncMode.X_CHECKPOINT;
    }

    @Bean
    TransactionPool transactionPool(ProtocolSchedule protocolSchedule,
                                    ProtocolContext protocolContext,
                                    EthContext ethContext,
                                    MetricsSystem metricsSystem,
                                    SyncState syncState,
                                    Clock clock,
                                    MiningParameters miningParameters,
                                    TransactionPoolConfiguration transactionPoolConfiguration) {
        return TransactionPoolFactory.createTransactionPool(
                protocolSchedule,
                protocolContext,
                ethContext,
                clock,
                metricsSystem,
                syncState,
                miningParameters,
                transactionPoolConfiguration);
    }

    @Bean
    MiningParameters miningParameters(MinerOptionProperties minerOptionProperties, MiningOptionsProperties miningOptionsProperties, BesuProperties besuProperties) {
        return new MiningParameters.Builder()
                .coinbase(minerOptionProperties.translateCoinbase())
                .targetGasLimit(besuProperties.getTargetGasLimit())
                .minTransactionGasPrice(DEFAULT_MIN_TRANSACTION_GAS_PRICE)
                .extraData(minerOptionProperties.getExtraData())
                .miningEnabled(minerOptionProperties.isMiningEnabled())
                .stratumMiningEnabled(minerOptionProperties.isStratumMiningEnabled())
                .stratumNetworkInterface(minerOptionProperties.getStratumNetworkInterface())
                .stratumPort(minerOptionProperties.getStratumPort())
                .stratumExtranonce(miningOptionsProperties.getStratumExtranonce())
                .minBlockOccupancyRatio(DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO)
                .remoteSealersLimit(miningOptionsProperties.getRemoteSealersLimit())
                .remoteSealersTimeToLive(miningOptionsProperties.getRemoteSealersTimeToLive())
                .powJobTimeToLive(miningOptionsProperties.getPowJobTimeToLive())
                .maxOmmerDepth(miningOptionsProperties.getMaxOmmersDepth())
                .posBlockCreationMaxTime(miningOptionsProperties.getPosBlockCreationMaxTime())
                .build();
    }

    @Bean
    ProtocolContext protocolContext(MutableBlockchain blockchain, WorldStateArchive worldStateArchive, ProtocolSchedule protocolSchedule, final ConsensusContextFactory consensusContextFactory) {
        return ProtocolContext.init(
                blockchain, worldStateArchive, protocolSchedule, consensusContextFactory);
    }

    @Bean
    protected ConsensusContextFactory consensusContextFactory(
            GenesisConfigOptions genesisConfigOptions, SyncState syncState) {
        {
            return (blockchain, worldStateArchive, protocolSchedule) -> {
                OptionalLong terminalBlockNumber = genesisConfigOptions.getTerminalBlockNumber();
                Optional<Hash> terminalBlockHash = genesisConfigOptions.getTerminalBlockHash();

                final MergeContext mergeContext =
                        PostMergeContext.get()
                                .setSyncState(syncState)
                                .setTerminalTotalDifficulty(
                                        genesisConfigOptions
                                                .getTerminalTotalDifficulty()
                                                .map(Difficulty::of)
                                                .orElse(Difficulty.ZERO));

                blockchain
                        .getFinalized()
                        .flatMap(blockchain::getBlockHeader)
                        .ifPresent(mergeContext::setFinalized);

                blockchain
                        .getSafeBlock()
                        .flatMap(blockchain::getBlockHeader)
                        .ifPresent(mergeContext::setSafeBlock);

                if (terminalBlockNumber.isPresent() && terminalBlockHash.isPresent()) {
                    Optional<BlockHeader> termBlock = blockchain.getBlockHeader(terminalBlockNumber.getAsLong());
                    mergeContext.setTerminalPoWBlock(termBlock);
                }
                blockchain.observeBlockAdded(
                        blockAddedEvent ->
                                blockchain
                                        .getTotalDifficultyByHash(blockAddedEvent.getBlock().getHeader().getHash())
                                        .ifPresent(mergeContext::setIsPostMerge));

                return mergeContext;
            };
        }
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SyncState syncState(MutableBlockchain blockchain, EthPeers ethPeers, SynchronizerConfiguration syncConfig) {
        return new SyncState(blockchain, ethPeers, !SyncMode.isFullSync(syncConfig.getSyncMode()), Optional.empty());
    }

    @Bean
    EthPeers ethPeers(Clock clock,
                      MetricsSystem metricsSystem,
                      EthProtocolConfiguration ethereumWireProtocolConfiguration,
                      P2PProperties p2PProperties) {
        return new EthPeers(
                EthProtocol.NAME,
                clock,
                metricsSystem,
                p2PProperties.getMaxPeers(),
                ethereumWireProtocolConfiguration.getMaxMessageSize(),
                Collections.emptyList());
    }

    @Bean
    EthProtocolConfiguration ethereumWireProtocolConfiguration() {
        return EthProtocolOptions.create().toDomainObject();
    }

    @Bean
    EthMessages ethMessages() {
        return new EthMessages();
    }

    @Bean
    EthMessages snapMessages() {
        return new EthMessages();
    }

    @Bean
    EthContext ethContext(EthPeers ethPeers, EthMessages ethMessages, EthMessages snapMessages, EthScheduler ethScheduler) {
        return new EthContext(ethPeers, ethMessages, snapMessages, ethScheduler);
    }

    @Bean
    EthScheduler ethScheduler(SynchronizerConfiguration syncConfig, MetricsSystem metricsSystem) {
        return
                new EthScheduler(
                        syncConfig.getDownloaderParallelism(),
                        syncConfig.getTransactionsParallelism(),
                        syncConfig.getComputationParallelism(),
                        metricsSystem);
    }

    @Bean
    TransactionPoolConfiguration transactionPoolConfiguration(TXPoolProperties TXPoolProperties) {
        return TransactionPoolOptions.create()
                .toDomainObject()
                .txPoolMaxSize(TXPoolProperties.getTxPoolMaxSize())
                .pendingTxRetentionPeriod(TXPoolProperties.getPendingTxRetentionPeriod())
                .priceBump(Percentage.fromInt(TXPoolProperties.getPriceBump()))
                .txFeeCap(TransactionPoolConfiguration.DEFAULT_RPC_TX_FEE_CAP)
                .build();
    }
}
