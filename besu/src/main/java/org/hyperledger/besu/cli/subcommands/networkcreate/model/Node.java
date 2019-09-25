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

import org.hyperledger.besu.cli.subcommands.networkcreate.generate.DirectoryHandler;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Generatable;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Verifiable;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.InitConfigurationErrorHandler;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.io.Resources;
import com.moandjiezana.toml.TomlWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO Handle errors
class Node implements Generatable, ConfigNode, Verifiable {

  private static final Logger LOG = LogManager.getLogger();
  private static final String PRIVATE_KEY_FILENAME = "key";
  private static final String CONFIG_FILENAME = "config.toml";
  private static final String CONFIG_TEMPLATE_FILENAME = "node-config-template.toml";
  private static final String DEFAULT_IP = "127.0.0.1";
  private static final int DEFAULT_P2P_PORT = 30303;
  private static final int DEFAULT_RPC_HTTP_PORT = 8545;
  private static final int DEFAULT_RPC_WS_PORT = 8546;
  private static final int DEFAULT_GRAPHQL_PORT = 8547;
  private static final String TOML_BOOTNODE_KEY = "bootnodes";
  private static final String TOML_BOOTNODE_FIND_REGEX = TOML_BOOTNODE_KEY + "=.*";
  private static final String TOML_P2P_PORT_KEY = "p2p-port";
  private static final String TOML_P2P_PORT_FIND_REGEX = TOML_P2P_PORT_KEY + "=[0-9]+";
  private static final String TOML_RPC_HTTP_PORT_KEY = "rpc-http-port";
  private static final String TOML_RPC_HTTP_PORT_FIND_REGEX = TOML_RPC_HTTP_PORT_KEY + "=[0-9]+";
  private static final String TOML_GRAPHQL_PORT_KEY = "graphql-http-port";
  private static final String TOML_GRAPHQL_PORT_FIND_REGEX = TOML_GRAPHQL_PORT_KEY + "=[0-9]+";
  private static final String TOML_RPC_WS_PORT_KEY = "rpc-ws-port";
  private static final String TOML_RPC_WS_PORT_FIND_REGEX = TOML_RPC_WS_PORT_KEY + "=[0-9]+";

  @JsonIgnore private final KeyPair keyPair;
  @JsonIgnore private final Address address;
  @JsonIgnore private final String p2pIp;
  private String name;
  private Boolean validator;
  private Boolean bootnode;
  private ConfigNode parent;

  public Node(
      @JsonProperty("name") final String name,
      @JsonProperty("validator") final Boolean validator,
      @JsonProperty("bootnode") final Boolean bootnode) {
    this.name = name;
    this.validator = requireNonNullElse(validator, false);
    this.bootnode = requireNonNullElse(bootnode, false);

    keyPair = SECP256K1.KeyPair.generate();
    address = Util.publicKeyToAddress(keyPair.getPublicKey());

    // TODO see how to get that right, not constants
    p2pIp = DEFAULT_IP;
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public String getName() {
    return name;
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public Boolean getBootnode() {
    return bootnode;
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public Address getAddress() {
    return address;
  }

  @JsonIgnore
  Boolean getValidator() {
    return validator;
  }

  @JsonIgnore
  public KeyPair getKeyPair() {
    return keyPair;
  }

  @JsonIgnore
  private EnodeURL getEnodeURL() {
    final int p2pPort = getPort(DEFAULT_P2P_PORT, 1);
    return EnodeURL.builder()
        .nodeId(keyPair.getPublicKey().getEncodedBytes())
        .ipAddress(p2pIp)
        .listeningPort(p2pPort)
        .discoveryPort(p2pPort)
        .build();
  }

  private Integer getPort(final Integer defaultPort, final int portGroupSize) {
    final List<Node> siblings = getSiblings();
    final int positionInNodesList = siblings.indexOf(this);
    // ensure no port collision on this network
    return defaultPort + positionInNodesList * portGroupSize;
  }

  private List<Node> getSiblings() {
    return ((Configuration) parent).getNodes();
  }

  @Override
  public Path generate(final Path outputDirectoryPath, final DirectoryHandler directoryHandler) {
    // generate node dir
    final Path nodeDir = outputDirectoryPath.resolve(directoryHandler.getSafeName(name));
    directoryHandler.create(nodeDir);

    // generate private key
    try {
      Files.write(
          nodeDir.resolve(PRIVATE_KEY_FILENAME),
          keyPair.getPrivateKey().toString().getBytes(UTF_8),
          StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      LOG.error("Unable to write private key file", e);
    }

    // generate config file
    createConfigFile(nodeDir);

    // generate privacy config if requested
    ((Configuration) parent)
        .getPrivacy()
        .ifPresent(privacy -> privacy.generate(nodeDir, directoryHandler));

    LOG.debug("Node {} address is {}", name, address);

    return nodeDir;
  }

  private void createConfigFile(final Path nodeDir) {
    final TomlWriter tomlWriter = new TomlWriter.Builder().build();

    try {
      final URL configTemplateFile =
          getClass().getClassLoader().getResource(CONFIG_TEMPLATE_FILENAME);
      checkNotNull(configTemplateFile, "Configuration template not found.");
      String configTemplateSource = Resources.toString(configTemplateFile, UTF_8);

      // Write bootnodes list if the node is not a bootnode.
      final List<Node> siblings = getSiblings();
      final List<URI> bootnodesEnodeURLs =
          siblings.stream()
              .filter(node -> node.bootnode && !node.equals(this))
              .map(Node::getEnodeURL)
              .map(EnodeURL::toURI)
              .collect(Collectors.toList());

      final Map<String, Object> bootnodes = new HashMap<>();
      bootnodes.put(TOML_BOOTNODE_KEY, bootnodesEnodeURLs);

      configTemplateSource =
          configTemplateSource.replaceAll(TOML_BOOTNODE_FIND_REGEX, tomlWriter.write(bootnodes));

      // Write ports
      configTemplateSource =
          replacePort(
              tomlWriter,
              configTemplateSource,
              TOML_P2P_PORT_KEY,
              TOML_P2P_PORT_FIND_REGEX,
              DEFAULT_P2P_PORT,
              1);

      configTemplateSource =
          replacePort(
              tomlWriter,
              configTemplateSource,
              TOML_RPC_HTTP_PORT_KEY,
              TOML_RPC_HTTP_PORT_FIND_REGEX,
              DEFAULT_RPC_HTTP_PORT,
              3);
      configTemplateSource =
          replacePort(
              tomlWriter,
              configTemplateSource,
              TOML_RPC_WS_PORT_KEY,
              TOML_RPC_WS_PORT_FIND_REGEX,
              DEFAULT_RPC_WS_PORT,
              3);
      configTemplateSource =
          replacePort(
              tomlWriter,
              configTemplateSource,
              TOML_GRAPHQL_PORT_KEY,
              TOML_GRAPHQL_PORT_FIND_REGEX,
              DEFAULT_GRAPHQL_PORT,
              3);

      // TODO customise TOML values

      Files.write(
          nodeDir.resolve(CONFIG_FILENAME),
          configTemplateSource.getBytes(UTF_8),
          StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      LOG.error("Unable to write node configuration file", e);
    }
  }

  private String replacePort(
      final TomlWriter tomlWriter,
      final String configTemplateSource,
      final String key,
      final String regex,
      final int defaultPort,
      final int portGroupSize) {
    final HashMap<String, Integer> valueMap = new HashMap<>();
    valueMap.put(key, getPort(defaultPort, portGroupSize));
    LOG.debug(valueMap);
    return configTemplateSource.replaceAll(regex, tomlWriter.write(valueMap));
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
  public InitConfigurationErrorHandler verify(final InitConfigurationErrorHandler errorHandler) {
    if (isNull(name)) errorHandler.add("Node name", "null", "Node name not defined.");
    return errorHandler;
  }
}
