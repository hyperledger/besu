/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.plugin.services.securitymodule.bouncycastle;

import org.hyperledger.besu.crypto.BouncyCastleSecurityModule;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.SecurityModuleService;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.plugin.services.securitymodule.bouncycastle.configuration.BouncyCastleSecurityModuleCLIOptions;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BouncyCastleSecurityModulePlugin implements BesuPlugin {
  private static final Logger LOG = LogManager.getLogger();
  private static final String PICOCLI_NAME = "securitymodule-bouncycastle";
  private final boolean isDocker = Boolean.getBoolean("besu.docker");
  private final BouncyCastleSecurityModuleCLIOptions cliOptions =
      new BouncyCastleSecurityModuleCLIOptions();
  private BesuConfiguration besuConfiguration;

  @Override
  public void register(final BesuContext context) {
    LOG.debug("Registering plugin");
    registerCliOptions(context);
    registerSecurityModule(context);
  }

  private void registerCliOptions(final BesuContext context) {
    final PicoCLIOptions picoCLIOptions =
        context
            .getService(PicoCLIOptions.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Expecting a PicoCLIOptions service to register CLI options with, but none found."));

    picoCLIOptions.addPicoCLIOptions(PICOCLI_NAME, cliOptions);
  }

  private void registerSecurityModule(final BesuContext context) {
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
    this.besuConfiguration = besuConfiguration;
    LOG.info("Node Private Key File: {}", nodePrivateKeyFile());
    return new BouncyCastleSecurityModule(this.loadKeyPair());
  }

  private SECP256K1.KeyPair loadKeyPair() {
    return KeyPairUtil.loadKeyPair(nodePrivateKeyFile());
  }

  public File nodePrivateKeyFile() {
    File nodePrivateKeyFile = null;
    if (isFullInstantiation()) {
      nodePrivateKeyFile = cliOptions.getPrivateKeyFile();
    }

    return nodePrivateKeyFile != null
        ? nodePrivateKeyFile
        : KeyPairUtil.getDefaultKeyFile(besuConfiguration.getDataPath());
  }

  private boolean isFullInstantiation() {
    return !isDocker;
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
