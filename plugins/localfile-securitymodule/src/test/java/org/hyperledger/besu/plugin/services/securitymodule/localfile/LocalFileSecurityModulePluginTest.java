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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hyperledger.besu.plugin.services.securitymodule.localfile.LocalFileSecurityModulePlugin.PICOCLI_NAMESPACE;
import static org.hyperledger.besu.plugin.services.securitymodule.localfile.LocalFileSecurityModulePlugin.SECURITY_MODULE_NAME;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.SecurityModuleService;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.plugin.services.securitymodule.localfile.configuration.LocalFileSecurityModuleCLIOptions;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.PicoCLIOptionsImpl;
import org.hyperledger.besu.services.SecurityModuleServiceImpl;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine;

@RunWith(MockitoJUnitRunner.class)
public class LocalFileSecurityModulePluginTest {
  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @Mock private BesuContext besuContext;
  private final SecurityModuleServiceImpl securityModuleService = new SecurityModuleServiceImpl();
  private CommandLine commandLine;

  @Before
  public void setUp() {
    commandLine = new CommandLine(TestCommand.class);

    when(besuContext.getService(PicoCLIOptions.class))
        .thenReturn(Optional.of(new PicoCLIOptionsImpl(commandLine)));
    when(besuContext.getService(SecurityModuleService.class))
        .thenReturn(Optional.of(securityModuleService));
  }

  @Before
  @After
  public void resetSystemProps() {
    System.setProperty("besu.docker", "false");
  }

  @Test
  public void cliOptionIsDisabledUnderDockerMode() {
    System.setProperty("besu.docker", "true");
    assumeTrue(Boolean.getBoolean("besu.docker"));

    final LocalFileSecurityModulePlugin localFileSecurityModulePlugin =
        new LocalFileSecurityModulePlugin();
    localFileSecurityModulePlugin.register(besuContext);

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                commandLine.parseArgs(
                    "--plugin-localfile-security-module-private-key-file",
                    "./specific/enclavePrivateKey"))
        .withMessage(
            "Unknown options: '--plugin-localfile-security-module-private-key-file', './specific/enclavePrivateKey'");
  }

  @Test
  public void cliOptionCanBeParsedInFullMode() {
    assumeFalse(Boolean.getBoolean("besu.docker"));

    final LocalFileSecurityModulePlugin localFileSecurityModulePlugin =
        new LocalFileSecurityModulePlugin();
    localFileSecurityModulePlugin.register(besuContext);

    commandLine.parseArgs(
        "--plugin-localfile-security-module-private-key-file", "./specific/enclavePrivateKey");
    final LocalFileSecurityModuleCLIOptions localFileSecurityModuleCLIOptions =
        (LocalFileSecurityModuleCLIOptions)
            commandLine.getMixins().get("Plugin " + PICOCLI_NAMESPACE);
    assertThat(localFileSecurityModuleCLIOptions.getPrivateKeyFile())
        .isEqualTo(new File("./specific/enclavePrivateKey"));
  }

  @Test
  public void cliOptionsCanBeParsedWithoutSpecifyingKeyFile() {
    assumeFalse(Boolean.getBoolean("besu.docker"));

    final LocalFileSecurityModulePlugin localFileSecurityModulePlugin =
        new LocalFileSecurityModulePlugin();
    localFileSecurityModulePlugin.register(besuContext);

    commandLine.parseArgs();

    final LocalFileSecurityModuleCLIOptions localFileSecurityModuleCLIOptions =
        (LocalFileSecurityModuleCLIOptions)
            commandLine.getMixins().get("Plugin " + PICOCLI_NAMESPACE);
    assertThat(localFileSecurityModuleCLIOptions).isNotNull();
  }

  @Test
  public void keyFromDataPathInBesuConfigurationIsUsedInDockerMode() throws IOException {
    System.setProperty("besu.docker", "true");
    assumeTrue(Boolean.getBoolean("besu.docker"));

    final LocalFileSecurityModulePlugin localFileSecurityModulePlugin =
        new LocalFileSecurityModulePlugin();
    localFileSecurityModulePlugin.register(besuContext);

    commandLine.parseArgs(); // we cannot specify key cli option in docker mode.

    final File dataFolder = temp.newFolder();

    // pre-create private Key in temp folder
    final SECP256K1.KeyPair keyPair = KeyPairUtil.loadKeyPair(dataFolder.toPath());

    final Optional<SecurityModule> securityModule =
        securityModuleService
            .getByName(SECURITY_MODULE_NAME)
            .map(
                secModule ->
                    secModule.create(
                        new BesuConfigurationImpl(dataFolder.toPath(), dataFolder.toPath())));
    assertThat(securityModule).isPresent();

    assertThat(securityModule.get().getPublicKey().getEncoded())
        .isEqualByComparingTo(keyPair.getPublicKey().getEncodedBytes());
  }

  @Test
  public void keyFromCLIOptionIsUsedInFullMode() throws IOException {
    System.setProperty("besu.docker", "false");
    assumeFalse(Boolean.getBoolean("besu.docker"));

    LocalFileSecurityModulePlugin localFileSecurityModulePlugin =
        new LocalFileSecurityModulePlugin();
    localFileSecurityModulePlugin.register(besuContext);

    final File keyDirectory = temp.newFolder();
    final File keyFile = new File(keyDirectory, "key");

    // pre-create private Key
    final SECP256K1.KeyPair keyPair = KeyPairUtil.loadKeyPair(keyFile);

    commandLine.parseArgs("--plugin-localfile-security-module-private-key-file", keyFile.getPath());

    final Optional<SecurityModule> securityModule =
        securityModuleService
            .getByName(SECURITY_MODULE_NAME)
            .map(secModule -> secModule.create(null));
    assertThat(securityModule).isPresent();

    assertThat(securityModule.get().getPublicKey().getEncoded())
        .isEqualByComparingTo(keyPair.getPublicKey().getEncodedBytes());
  }
}
