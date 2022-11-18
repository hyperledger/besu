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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DefaultWorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValuePrivacyStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.StorageServiceImpl;
import org.hyperledger.besu.springmain.config.properties.BesuProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;

public class BlockchainConfiguration {


    @Bean
    public MutableBlockchain blockchain(GenesisState genesisState,
                                        BlockchainStorage blockchainStorage,
                                        MetricsSystem metricsSystem,
                                        BesuConfiguration besuConfiguration,
                                        BesuProperties besuProperties) {
        return DefaultBlockchain.createMutable(
                genesisState.getBlock(),
                blockchainStorage,
                metricsSystem,
                besuProperties.getReorgLoggingThreshold(),
                besuConfiguration.getDataPath().toString());
    }

    @Bean
    BlockchainStorage blockchainStorage(StorageProvider storageProvider, ProtocolSchedule protocolSchedule) {
        return storageProvider.createBlockchainStorage(protocolSchedule);
    }

    @Bean
    StorageProvider storageProvider(StorageService storageService, MetricsSystem metricsSystem, BesuConfiguration besuConfiguration,
                                    BesuProperties besuProperties) {
        return
                new KeyValueStorageProviderBuilder()
                        .withStorageFactory(
                                storageService
                                        .getByName(besuProperties.getKeyValueStorageName())
                                        .orElseThrow(
                                                () ->
                                                        new StorageException(
                                                                "No KeyValueStorageFactory found for key: " + besuProperties.getKeyValueStorageName())))
                        .withCommonConfiguration(besuConfiguration)
                        .withMetricsSystem(metricsSystem)
                        .isGoQuorumCompatibilityMode(false)
                        .build();

    }

    @Bean
    BesuConfiguration besuConfiguration(BesuProperties besuProperties) {
        final Path dataDir = Path.of(besuProperties.getDataPath());
        return new BesuConfigurationImpl(dataDir, dataDir.resolve(DATABASE_PATH));
    }

    @Bean
    StorageService storageService(RocksDBCLIOptions options) {
        final StorageServiceImpl service = new StorageServiceImpl();


        final List<SegmentIdentifier> segments = service.getAllSegmentIdentifiers();

        final Supplier<RocksDBFactoryConfiguration> configuration =
                Suppliers.memoize(options::toDomainObject);
        RocksDBKeyValueStorageFactory factory =
                new RocksDBKeyValueStorageFactory(
                        configuration, segments, RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS);
        RocksDBKeyValuePrivacyStorageFactory privacyFactory = new RocksDBKeyValuePrivacyStorageFactory(factory);

        service.registerKeyValueStorage(factory);
        service.registerKeyValueStorage(privacyFactory);

        return service;
    }

    @Bean
    RocksDBCLIOptions options() {
        return RocksDBCLIOptions.create();
    }

    @Bean
    public GenesisState genesisState(GenesisConfigFile genesisConfig, ProtocolSchedule protocolSchedule) {
        return GenesisState.fromConfig(genesisConfig, protocolSchedule);
    }

    @Bean
    GenesisConfigFile genesisConfigFile() {
        return GenesisConfigFile.mainnet();
    }

    @Bean
    protected ProtocolSchedule createProtocolSchedule(GenesisConfigOptions genesisConfigFile,
                                                      PrivacyParameters privacyParameters,
                                                      EvmConfiguration evmConfiguration,
                                                      BesuProperties besuProperties) {
        return MainnetProtocolSchedule.fromConfig(
                genesisConfigFile, privacyParameters, besuProperties.isRevertReasonEnabled(), evmConfiguration);
    }

    @Bean
    GenesisConfigOptions genesisConfigOptions(GenesisConfigFile genesisConfigFile) {
        return genesisConfigFile.getConfigOptions();
    }

    @Bean
    PrivacyParameters privacyParameters() {
        return PrivacyParameters.DEFAULT;
    }

    @Bean
    EvmConfiguration evmConfiguration() {
        return EvmConfiguration.DEFAULT;
    }

    @Bean
    public WorldStateArchive worldStateArchive(StorageProvider storageProvider, DataStorageConfiguration dataStorageConfiguration, Blockchain blockchain, WorldStateStorage worldStateStorage) {

        switch (dataStorageConfiguration.getDataStorageFormat()) {
            case BONSAI:
                return new BonsaiWorldStateArchive(
                        (BonsaiWorldStateKeyValueStorage) worldStateStorage,
                        blockchain,
                        Optional.of(dataStorageConfiguration.getBonsaiMaxLayersToLoad()),
                        dataStorageConfiguration.useBonsaiSnapshots());

            case FOREST:
            default:
                final WorldStatePreimageStorage preimageStorage =
                        storageProvider.createWorldStatePreimageStorage();
                return new DefaultWorldStateArchive(worldStateStorage, preimageStorage);
        }
    }

    @Bean
    public WorldStateStorage worldStateStorage(StorageProvider storageProvider, DataStorageConfiguration dataStorageConfiguration) {
        return storageProvider.createWorldStateStorage(dataStorageConfiguration.getDataStorageFormat());
    }

    @Bean
    public DataStorageConfiguration dataStorageConfiguration() {
        return DataStorageConfiguration.DEFAULT_CONFIG;
    }

}
