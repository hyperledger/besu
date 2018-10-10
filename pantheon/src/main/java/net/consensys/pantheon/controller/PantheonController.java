package net.consensys.pantheon.controller;

import net.consensys.pantheon.consensus.clique.CliqueProtocolSchedule;
import net.consensys.pantheon.consensus.ibft.IbftProtocolSchedule;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator;
import net.consensys.pantheon.ethereum.blockcreation.MiningParameters;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.core.Synchronizer;
import net.consensys.pantheon.ethereum.core.TransactionPool;
import net.consensys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

import io.vertx.core.json.JsonObject;

public interface PantheonController<C> extends Closeable {

  String DATABASE_PATH = "database";

  static PantheonController<?> fromConfig(
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

  MiningCoordinator getMiningCoordinator();
}
