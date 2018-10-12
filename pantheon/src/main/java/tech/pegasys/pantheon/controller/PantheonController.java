package tech.pegasys.pantheon.controller;

import tech.pegasys.pantheon.consensus.clique.CliqueProtocolSchedule;
import tech.pegasys.pantheon.consensus.ibft.IbftProtocolSchedule;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockCreator;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import tech.pegasys.pantheon.ethereum.blockcreation.BlockMiner;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningParameters;
import tech.pegasys.pantheon.ethereum.chain.GenesisConfig;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

import io.vertx.core.json.JsonObject;

public interface PantheonController<C, M extends BlockMiner<C, ? extends AbstractBlockCreator<C>>>
    extends Closeable {

  String DATABASE_PATH = "database";

  static PantheonController<?, ?> fromConfig(
      final SynchronizerConfiguration syncConfig,
      final String configContents,
      final Path pantheonHome,
      final boolean ottomanTestnetOperation,
      final int networkId,
      final MiningParameters miningParameters,
      final KeyPair nodeKeys)
      throws IOException {

    final JsonObject config = new JsonObject(configContents);
    final JsonObject configOptions = config.getJsonObject("config");

    if (configOptions.containsKey("ethash")) {
      return MainnetPantheonController.init(
          pantheonHome,
          GenesisConfig.fromConfig(config, MainnetProtocolSchedule.fromConfig(configOptions)),
          syncConfig,
          miningParameters,
          networkId,
          nodeKeys);
    } else if (configOptions.containsKey("ibft")) {
      return IbftPantheonController.init(
          pantheonHome,
          GenesisConfig.fromConfig(config, IbftProtocolSchedule.create(configOptions)),
          syncConfig,
          ottomanTestnetOperation,
          configOptions.getJsonObject("ibft"),
          networkId,
          nodeKeys);
    } else if (configOptions.containsKey("clique")) {
      return CliquePantheonController.init(
          pantheonHome,
          GenesisConfig.fromConfig(config, CliqueProtocolSchedule.create(configOptions, nodeKeys)),
          syncConfig,
          miningParameters,
          configOptions.getJsonObject("clique"),
          networkId,
          nodeKeys);
    } else {
      throw new IllegalArgumentException("Unknown consensus mechanism defined");
    }
  }

  default ProtocolSchedule<C> getProtocolSchedule() {
    return getGenesisConfig().getProtocolSchedule();
  }

  ProtocolContext<C> getProtocolContext();

  GenesisConfig<C> getGenesisConfig();

  Synchronizer getSynchronizer();

  SubProtocolConfiguration subProtocolConfiguration();

  KeyPair getLocalNodeKeyPair();

  TransactionPool getTransactionPool();

  AbstractMiningCoordinator<C, M> getMiningCoordinator();
}
