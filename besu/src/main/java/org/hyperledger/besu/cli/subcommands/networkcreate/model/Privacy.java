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
package org.hyperledger.besu.cli.subcommands.networkcreate.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNullElse;

import org.hyperledger.besu.cli.subcommands.networkcreate.generate.DirectoryHandler;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Generatable;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Verifiable;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.InitConfigurationErrorHandler;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.io.Resources;
import com.moandjiezana.toml.TomlWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO Handle errors
class Privacy implements ConfigNode, Verifiable, Generatable {

  private static final Logger LOG = LogManager.getLogger();

  private static final String PRIVACY_DIR_NAME = "Orion";
  private static final String PASSWORD_FILENAME = "passwordFile";
  private static final String PRIVATE_KEY_FILENAME = "nodeKey.key";
  private static final String PUBLIC_KEY_FILENAME = "nodeKey.pub";
  private static final String CONFIG_TEMPLATE_FILENAME = "orion-template.conf";
  private static final String CONFIG_FILENAME = "orion.conf";

  private static final String TOML_TLS_KEY = "tls";
  private static final String TOML_TLS_FIND_REGEX = TOML_TLS_KEY + ".*=.+";

  private ConfigNode parent;

  private Boolean tls;

  public Privacy(@JsonProperty("tls") final Boolean tls) {
    this.tls = requireNonNullElse(tls, false);
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public Boolean getTls() {
    return tls;
  }

  @Override
  public void setParent(final ConfigNode parent) {
    this.parent = parent;
  }

  @Override
  public ConfigNode getParent() {
    return parent;
  }

  @Override
  public Path generate(final Path outputDirectoryPath, final DirectoryHandler directoryHandler) {
    final Path privacyNodeDir = outputDirectoryPath.resolve(PRIVACY_DIR_NAME);
    directoryHandler.create(privacyNodeDir);

    // TODO create passwordFile
    try {
      Files.write(
          privacyNodeDir.resolve(PASSWORD_FILENAME),
          "password".getBytes(UTF_8),
          StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      LOG.error("Unable to write password file", e);
    }

    // TODO Generate Orion key pair
    try {
      Files.write(
          privacyNodeDir.resolve(PRIVATE_KEY_FILENAME),
          "private key".getBytes(UTF_8),
          StandardOpenOption.CREATE_NEW);
      Files.write(
          privacyNodeDir.resolve(PUBLIC_KEY_FILENAME),
          "public key".getBytes(UTF_8),
          StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      LOG.error("Unable to write key files", e);
    }

    createConfigFile(privacyNodeDir);
    return null;
  }

  @Override
  public InitConfigurationErrorHandler verify(final InitConfigurationErrorHandler errorHandler) {
    return errorHandler;
  }

  private void createConfigFile(final Path privacyNodeDir) {
    final TomlWriter tomlWriter = new TomlWriter.Builder().build();

    try {
      final URL configTemplateFile =
          getClass().getClassLoader().getResource(CONFIG_TEMPLATE_FILENAME);
      checkNotNull(configTemplateFile, "Configuration template not found.");
      String configTemplateSource = Resources.toString(configTemplateFile, UTF_8);

      // TODO customise TOML values
      //      // Write ports
      //      configTemplateSource =
      //          replacePort(
      //              tomlWriter,
      //              configTemplateSource,
      //              TOML_P2P_PORT_KEY,
      //              TOML_P2P_PORT_FIND_REGEX,
      //              DEFAULT_P2P_PORT,
      //              1);

      final HashMap<String, String> tlsValueMap = new HashMap<>();
      tlsValueMap.put("tls", tls ? "on" : "off");

      configTemplateSource =
          configTemplateSource.replaceAll(TOML_TLS_FIND_REGEX, tomlWriter.write(tlsValueMap));
      LOG.debug(configTemplateSource);
      Files.write(
          privacyNodeDir.resolve(CONFIG_FILENAME),
          configTemplateSource.getBytes(UTF_8),
          StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      LOG.error("Unable to write privacy configuration file", e);
    }
  }

  //  private String replacePort(
  //      final TomlWriter tomlWriter,
  //      final String configTemplateSource,
  //      final String key,
  //      final String regex,
  //      final int defaultPort,
  //      final int portGroupSize) {
  //    final HashMap<String, Integer> valueMap = new HashMap<>();
  //    valueMap.put(key, getPort(defaultPort, portGroupSize));
  //    LOG.debug(valueMap);
  //    return configTemplateSource.replaceAll(regex, tomlWriter.write(valueMap));
  //  }
}
