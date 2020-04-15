package org.hyperledger.besu.plugin.services.nodekey.bouncycastle;

import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.NodeKeySecurityModuleService;
import org.hyperledger.besu.plugin.services.nodekey.SecurityModule;

import java.nio.file.Path;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NodeKeyBouncyCastlePlugin implements BesuPlugin {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public void register(final BesuContext context) {
    LOG.debug("Registering plugin");
    context
        .getService(NodeKeySecurityModuleService.class)
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Node Key Security Module Service not available, Besu cannot start."))
        .registerNodeKeySecurityModule("bouncycastle", this::createBouncyCastleSecurityModule);
  }

  private SecurityModule createBouncyCastleSecurityModule(
      final BesuConfiguration besuConfiguration) {
    final String privateKeyFile =
        Optional.ofNullable(besuConfiguration.getAdditionalConfiguration().get("privateKeyFile"))
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Private Key File configuration not found. Besu cannot start."));

    return new BouncyCastleSecurityModule(
        KeyPairUtil.loadKeyPair(Path.of(privateKeyFile).toFile()));
  }

  @Override
  public void start() {
    LOG.debug("Starting plugin.");
  }

  @Override
  public void stop() {
    LOG.debug("Stopping plugin.");
  }
}
