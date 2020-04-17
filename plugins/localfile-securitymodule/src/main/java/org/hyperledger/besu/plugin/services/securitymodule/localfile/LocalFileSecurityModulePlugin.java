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
package org.hyperledger.besu.plugin.services.securitymodule.localfile;

import org.hyperledger.besu.crypto.KeyPairSecurityModule;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.SecurityModuleService;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.plugin.services.securitymodule.localfile.configuration.LocalFileSecurityModuleCLIOptions;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LocalFileSecurityModulePlugin implements BesuPlugin {
  public static final String PICOCLI_NAMESPACE = "localfile-security-module";

  private static final Logger LOG = LogManager.getLogger();
  static final String SECURITY_MODULE_NAME = "localfile";
  private final boolean isDocker = Boolean.getBoolean("besu.docker");
  private final LocalFileSecurityModuleCLIOptions cliOptions =
      new LocalFileSecurityModuleCLIOptions();
  private BesuConfiguration besuConfiguration;

  @Override
  public void register(final BesuContext context) {
    LOG.debug("Registering plugin");
    registerCliOptions(context);
    registerSecurityModule(context);
  }

  private void registerCliOptions(final BesuContext context) {
    if (!isFullInstantiation()) {
      return; // don't register cli options in docker mode
    }

    final PicoCLIOptions picoCLIOptions =
        context
            .getService(PicoCLIOptions.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Expecting a PicoCLIOptions service to register CLI options with, but none found."));

    picoCLIOptions.addPicoCLIOptions(PICOCLI_NAMESPACE, cliOptions);
  }

  private void registerSecurityModule(final BesuContext context) {
    context
        .getService(SecurityModuleService.class)
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Security Module Service Not available, cannot register Service Module: "
                        + SECURITY_MODULE_NAME))
        .registerSecurityModule(SECURITY_MODULE_NAME, this::createFileBasedKeyPairSecurityModule);
  }

  private SecurityModule createFileBasedKeyPairSecurityModule(
      final BesuConfiguration besuConfiguration) {
    this.besuConfiguration = besuConfiguration;
    final File privateKeyFile = nodePrivateKeyFile();
    final SECP256K1.KeyPair keyPair = KeyPairUtil.loadKeyPair(privateKeyFile);
    return new KeyPairSecurityModule(keyPair);
  }

  private File nodePrivateKeyFile() {
    final File nodePrivateKeyFile = isFullInstantiation() ? cliOptions.getPrivateKeyFile() : null;

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
