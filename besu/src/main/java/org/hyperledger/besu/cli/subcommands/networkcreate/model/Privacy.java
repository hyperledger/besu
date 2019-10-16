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
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNullElse;
import static org.hyperledger.besu.cli.subcommands.networkcreate.generate.PortConfig.PRIVACY_CLIENT;
import static org.hyperledger.besu.cli.subcommands.networkcreate.generate.PortConfig.PRIVACY_NODE;

import org.hyperledger.besu.cli.subcommands.networkcreate.generate.ConfigWriter;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.DirectoryHandler;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Generatable;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.OrionKeys;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Verifiable;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.InitConfigurationErrorHandler;
import org.hyperledger.besu.crypto.SecureRandomProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.io.Base64;

// TODO Handle errors
class Privacy implements ConfigNode, Verifiable, Generatable {

  private static final Logger LOG = LogManager.getLogger();

  private static final String PRIVACY_DIR_NAME = "Orion";
  private static final String PASSWORD_FILENAME = "passwordFile";
  private static final String PRIVATE_KEY_FILENAME = "nodeKey.key";
  private static final String PUBLIC_KEY_FILENAME = "nodeKey.pub";
  private static final String CONFIG_FILENAME = "orion.conf";
  private static final String URL_FORMAT = "http://127.0.0.1:%1$s/";

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
  public Path generate(
      final Path outputDirectoryPath,
      final DirectoryHandler directoryHandler,
      @Nullable final Node node) {
    final Path privacyNodeDir = outputDirectoryPath.resolve(PRIVACY_DIR_NAME);
    directoryHandler.create(privacyNodeDir);

    String password = null;
    try {
      // generate a 20 chars password with chars from the range between ! (33) and ~ (126) in ascii
      // table
      // it includes all printable symbols from letters to numbers and special characters.
      password =
          SecureRandomProvider.createSecureRandom()
              .ints(20, '!', '~')
              .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
              .toString();

      Files.write(
          privacyNodeDir.resolve(PASSWORD_FILENAME),
          password.getBytes(UTF_8),
          StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      LOG.error("Unable to write password file", e);
    }

    // generate key pair and write:
    // - encrypted private key using password in a .key file
    // - public key in a .pub file
    try {
      checkNotNull(password);
      OrionKeys orionKeys = new OrionKeys();

      // Write private key encrypted and wrapped in Json
      Files.write(
          privacyNodeDir.resolve(PRIVATE_KEY_FILENAME),
          orionKeys.encryptToJson(password).getBytes(UTF_8),
          StandardOpenOption.CREATE_NEW);

      // Write public key
      Path publicKeyFile = privacyNodeDir.resolve(PUBLIC_KEY_FILENAME);
      if (!isNull(node)) {
        node.setPrivacyNodePublicKeyFile(publicKeyFile);
      }
      Files.write(
          publicKeyFile,
          Base64.encodeBytes(orionKeys.getPublicKey().bytesArray()).getBytes(UTF_8),
          StandardOpenOption.CREATE_NEW);
    } catch (NullPointerException e) {
      LOG.error("Error happened while generating password", e);
    } catch (IOException e) {
      LOG.error("Unable to write Orion key files", e);
    }

    createConfigFile(privacyNodeDir, node);
    return privacyNodeDir;
  }

  @Override
  public InitConfigurationErrorHandler verify(final InitConfigurationErrorHandler errorHandler) {
    return errorHandler;
  }

  private void createConfigFile(final Path configFileDir, final Node currentNode) {

    List<Node> allNodes = ((Configuration) parent).getNodes();

    Integer nodePort = PRIVACY_NODE.getPort(allNodes, currentNode);
    Integer clientPort = PRIVACY_CLIENT.getPort(allNodes, currentNode);
    String nodeUrl = String.format(URL_FORMAT, nodePort);
    String clientUrl = String.format(URL_FORMAT, clientPort);

    // used by node in its config file
    currentNode.setPrivacyClientUrl(clientUrl);

    String[] otherNodesUrls =
        allNodes.stream()
            .filter(node -> !node.equals(currentNode))
            .map(node -> String.format(URL_FORMAT, PRIVACY_NODE.getPort(allNodes, node)))
            .toArray(String[]::new);

    ConfigWriter configWriter = new ConfigWriter();
    configWriter
        .addComment("Orion configuration file.")
        // Write node options
        .addEmptyLine()
        .addOption("tls", tls)
        .addOption("nodeurl", nodeUrl)
        .addOption(PRIVACY_NODE.getKey(), nodePort)
        .addOption("clienturl", clientUrl)
        .addOption(PRIVACY_CLIENT.getKey(), clientPort)
        .addOption("privatekeys", new String[] {"nodeKey.key"})
        .addOption("publickeys", new String[] {"nodeKey.pub"})
        .addOption("passwords", "passwordFile")
        .addOption("othernodes", otherNodesUrls);
    // Write the file
    configWriter.write(configFileDir.resolve(CONFIG_FILENAME));
  }
}
