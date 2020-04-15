package org.hyperledger.besu.plugin.services.securitymodule.bouncycastle;

import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.SecurityModuleService;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;

import java.nio.file.Path;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BouncyCastleSecurityModulePlugin implements BesuPlugin {
  private static final Logger LOG = LogManager.getLogger();
  private static final String PICOCLI_NAME = "securitymodule-bouncycastle";
  @Override
  public void register(final BesuContext context) {
    LOG.debug("Registering plugin");
    final Optional<PicoCLIOptions> cmdlineOptions = context.getService(PicoCLIOptions.class);

    if (cmdlineOptions.isEmpty()) {
      throw new IllegalStateException(
              "Expecting a PicoCLIO options to register CLI options with, but none found.");
    }

    cmdlineOptions.get().addPicoCLIOptions(PICOCLI_NAME, options);

    context
        .getService(SecurityModuleService.class)
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Bouncy Castle Security Module Service not available, Besu cannot start."))
        .registerSecurityModule("bouncycastle", this::createBouncyCastleSecurityModule);
  }

  private SecurityModule createBouncyCastleSecurityModule(
      final BesuConfiguration besuConfiguration) {
    final String privateKeyFile = null; // TODO: From cli option


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
