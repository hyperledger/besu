package tech.pegasys.pantheon.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.pantheon.controller.KeyPairUtil.loadKeyPair;

import tech.pegasys.pantheon.controller.MainnetPantheonController;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningParameters;
import tech.pegasys.pantheon.ethereum.chain.GenesisConfig;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;

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
