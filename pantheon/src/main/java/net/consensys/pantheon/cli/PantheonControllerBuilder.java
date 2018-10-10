package net.consensys.pantheon.cli;

import static net.consensys.pantheon.controller.KeyPairUtil.loadKeyPair;

import net.consensys.pantheon.controller.MainnetPantheonController;
import net.consensys.pantheon.controller.PantheonController;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.blockcreation.MiningParameters;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PantheonControllerBuilder {

  public PantheonController<?> build(
      final SynchronizerConfiguration synchronizerConfiguration,
      final File genesisFile,
      final Path homePath,
      final boolean syncWithOttoman,
      final MiningParameters miningParameters,
      final boolean isDevMode,
      final int networkId)
      throws IOException {

    // instantiate a controller with mainnet config if no genesis file is defined
    // otherwise use the indicated genesis file
    final KeyPair nodeKeys = loadKeyPair(homePath);
    if (genesisFile == null) {
      final GenesisConfig<Void> genesisConfig =
          isDevMode ? GenesisConfig.development() : GenesisConfig.mainnet();
      return MainnetPantheonController.init(
          homePath,
          genesisConfig,
          synchronizerConfiguration,
          miningParameters,
          networkId,
          nodeKeys);
    } else {
      return PantheonController.fromConfig(
          synchronizerConfiguration,
          new String(Files.readAllBytes(genesisFile.toPath()), StandardCharsets.UTF_8),
          homePath,
          syncWithOttoman,
          networkId,
          miningParameters,
          nodeKeys);
    }
  }
}
