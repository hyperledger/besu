package net.consensys.pantheon.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.consensys.pantheon.controller.KeyPairUtil.loadKeyPair;

import net.consensys.pantheon.controller.MainnetPantheonController;
import net.consensys.pantheon.controller.PantheonController;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.blockcreation.MiningParameters;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;

import java.io.IOException;
import java.nio.file.Path;

import com.google.common.io.Resources;

public class PantheonControllerBuilder {

  public PantheonController<?, ?> build(
      final SynchronizerConfiguration synchronizerConfiguration,
      final Path homePath,
      final EthNetworkConfig ethNetworkConfig,
      final boolean syncWithOttoman,
      final MiningParameters miningParameters,
      final boolean isDevMode)
      throws IOException {
    // instantiate a controller with mainnet config if no genesis file is defined
    // otherwise use the indicated genesis file
    final KeyPair nodeKeys = loadKeyPair(homePath);
    if (isDevMode) {
      return MainnetPantheonController.init(
          homePath,
          GenesisConfig.development(),
          synchronizerConfiguration,
          miningParameters,
          ethNetworkConfig.getNetworkId(),
          nodeKeys);
    } else {
      final String genesisConfig =
          Resources.toString(ethNetworkConfig.getGenesisConfig().toURL(), UTF_8);
      return PantheonController.fromConfig(
          synchronizerConfiguration,
          genesisConfig,
          homePath,
          syncWithOttoman,
          ethNetworkConfig.getNetworkId(),
          miningParameters,
          nodeKeys);
    }
  }
}
