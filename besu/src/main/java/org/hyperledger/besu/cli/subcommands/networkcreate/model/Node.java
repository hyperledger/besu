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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNullElse;
import static org.hyperledger.besu.cli.subcommands.networkcreate.generate.PortConfig.GRAPHQL;
import static org.hyperledger.besu.cli.subcommands.networkcreate.generate.PortConfig.METRICS;
import static org.hyperledger.besu.cli.subcommands.networkcreate.generate.PortConfig.P2P;
import static org.hyperledger.besu.cli.subcommands.networkcreate.generate.PortConfig.RPC_HTTP;
import static org.hyperledger.besu.cli.subcommands.networkcreate.generate.PortConfig.RPC_WS;

import org.hyperledger.besu.cli.subcommands.networkcreate.generate.ConfigWriter;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Node implements Generatable, ConfigNode, Verifiable {

  private static final Logger LOG = LogManager.getLogger();
  private static final String PRIVATE_KEY_FILENAME = "key";
  private static final String DEFAULT_IP = "127.0.0.1";
  private static final String DEFAULT_INTERFACE = "0.0.0.0";
  private static final String CONFIG_FILENAME = "config.toml";

  @JsonIgnore private ConfigNode parent;

  @JsonIgnore private String privacyClientUrl;
  @JsonIgnore private Path publicKeyFile;

  @JsonIgnore private final KeyPair keyPair;
  @JsonIgnore private final Address address;
  private final String name;
  private final Boolean validator;
  private final Boolean bootnode;
  private final List<Apis> apis;

  @JsonCreator(mode = Mode.PROPERTIES)
  public Node(
      @JsonProperty("name") final String name,
      @JsonProperty("validator") final Boolean validator,
      @JsonProperty("bootnode") final Boolean bootnode,
      @JsonProperty("apis") final List<Apis> apis) {

    this.name = name;
    this.validator = requireNonNullElse(validator, false);
    this.bootnode = requireNonNullElse(bootnode, false);

    this.apis = requireNonNullElse(apis, List.of());

    keyPair = SECP256K1.KeyPair.generate();
    address = Util.publicKeyToAddress(keyPair.getPublicKey());
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

  @SuppressWarnings("unused") // Used by Jackson serialisation
  @JsonInclude(Include.NON_EMPTY)
  public List<Apis> getApis() {
    return apis;
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
    return EnodeURL.builder()
        .nodeId(keyPair.getPublicKey().getEncodedBytes())
        .ipAddress(DEFAULT_IP)
        .listeningPort(P2P.getPort(getSiblings(), this))
        .discoveryPort(P2P.getPort(getSiblings(), this))
        .build();
  }

  @JsonIgnore
  private List<Node> getSiblings() {
    return ((Configuration) parent).getNodes();
  }

  @JsonIgnore
  private Optional<String> getPrivacyClientUrl() {
    return Optional.ofNullable(privacyClientUrl);
  }

  @JsonIgnore
  void setPrivacyClientUrl(String privacyClientUrl) {
    this.privacyClientUrl = privacyClientUrl;
  }

  @JsonIgnore
  void setPrivacyNodePublicKeyFile(final Path publicKeyFile) {
    this.publicKeyFile = publicKeyFile;
  }

  @JsonIgnore
  private Optional<Path> getPrivacyNodePublicKeyFile() {
    return Optional.ofNullable(publicKeyFile);
  }

  @Override
  public Path generate(
      final Path outputDirectoryPath,
      final DirectoryHandler directoryHandler,
      @Nullable final Node node) {
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

    // generate privacy config if requested, must be generated before node config file
    // otherwise values are not populated
    ((Configuration) parent)
        .getPrivacy()
        .ifPresent(privacy -> privacy.generate(nodeDir, directoryHandler, this));

    LOG.debug("Node {} address is {}", name, address);

    // generate config file
    createConfigFile(nodeDir);

    return nodeDir;
  }

  private void createConfigFile(final Path nodeDir) {

    ConfigWriter configWriter = new ConfigWriter();
    configWriter
        .addComment(String.format("Node \"%1$s\" configuration file.", name))

        // Write node options
        .addEmptyLine()
        .addComment("Data path")
        .addOption("data-path", ".")

        // Write P2P port option
        .addEmptyLine()
        .addComment("P2P options")
        // Write bootnodes list if the node is not a bootnode.
        .addComment("Node bootnodes list")
        .addOption(
            "bootnodes",
            getSiblings().stream()
                .filter(node -> node.bootnode && !node.equals(this))
                .map(Node::getEnodeURL)
                .map(EnodeURL::toURI)
                .toArray(URI[]::new))
        .addComment("P2P advertising host")
        .addOption("p2p-host", DEFAULT_IP)
        .addComment("P2P listening port")
        .addOption(P2P.getKey(), P2P.getPort(getSiblings(), this))
        .addComment("P2P listening interface")
        .addOption("p2p-interface", DEFAULT_INTERFACE)
        .addComment("P2P hosts whitelist")
        .addOption("host-whitelist", new String[] {"all"});

    if (isApiEnabled(Apis.HTTP_RPC)) {
      // Write RPC HTTP options
      configWriter
          .addEmptyLine()
          .addComment("HTTP RPC options")
          .addOption("rpc-http-enabled", true)
          .addComment("HTTP RPC listening host")
          .addOption("rpc-http-host", "127.0.0.1")
          .addComment("HTTP RPC listening port")
          .addOption(RPC_HTTP.getKey(), RPC_HTTP.getPort(getSiblings(), this))
          .addComment("HTTP RPC activated APIs")
          .addOption("rpc-http-api", new String[] {"ETH", "NET", "WEB3"})
          .addComment("HTTP RPC cors")
          .addOption("rpc-http-cors-origins", new String[] {"all"});
    }

    if (isApiEnabled(Apis.WS_RPC)) {
      // Write RPC WebSocket options
      configWriter
          .addEmptyLine()
          .addComment("Websocket RPC options")
          .addOption("rpc-ws-enabled", true)
          .addComment("Websocket RPC listening host")
          .addOption("rpc-ws-host", "127.0.0.1")
          .addComment("Websocket RPC listening port")
          .addOption(RPC_WS.getKey(), RPC_WS.getPort(getSiblings(), this))
          .addComment("Websocket RPC activated APIs")
          .addOption("rpc-ws-api", new String[] {"ETH", "NET", "WEB3"});
    }

    if (isApiEnabled(Apis.GRAPHQL)) {
      // Write GraphQL options
      configWriter
          .addEmptyLine()
          .addComment("GRAPHQL options")
          .addOption("graphql-http-enabled", true)
          .addComment("GraphQL listening host")
          .addOption("graphql-http-host", "127.0.0.1")
          .addComment("GraphQL listening port")
          .addOption(GRAPHQL.getKey(), GRAPHQL.getPort(getSiblings(), this))
          .addComment("GraphQL cors")
          .addOption("graphql-http-cors-origins", new String[] {"all"});
    }

    if (isApiEnabled(Apis.METRICS)) {
      // Write Metrics options
      configWriter
          .addEmptyLine()
          .addComment("Metrics options")
          .addOption("metrics-enabled", true)
          .addComment("Metrics listening host")
          .addOption("metrics-host", "127.0.0.1")
          .addComment("Metrics listening port")
          .addOption(METRICS.getKey(), METRICS.getPort(getSiblings(), this));
    }

    if (((Configuration) parent).getPrivacy().isPresent()) {
      // Write Privacy options
      configWriter
          .addEmptyLine()
          .addComment("Privacy options")
          .addOption("privacy-enabled", true)
          .addOption("privacy-url", getPrivacyClientUrl().orElse("http://127.0.0.1:8888"))
          .addOption(
              "privacy-public-key-file",
              nodeDir
                  .relativize(
                      getPrivacyNodePublicKeyFile().orElse(nodeDir.resolve("Orion/nodeKey.pub")))
                  .toString())
          .addOption("min-gas-price", 0);
    }

    // Write the file
    configWriter.write(nodeDir.resolve(CONFIG_FILENAME));
  }

  private boolean isApiEnabled(final Apis api) {
    return apis.contains(api) || apis.contains(Apis.ALL);
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
