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

import org.hyperledger.besu.cli.subcommands.networkcreate.generate.DirectoryHandler;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Generatable;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.NodeConfig;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Verifiable;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.InitConfigurationErrorHandler;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO Handle errors
public class Node implements Generatable, ConfigNode, Verifiable {

  private static final Logger LOG = LogManager.getLogger();
  private static final String PRIVATE_KEY_FILENAME = "key";
  private static final String DEFAULT_IP = "127.0.0.1";

  @JsonIgnore private final KeyPair keyPair;
  @JsonIgnore private final Address address;
  @JsonIgnore private final String p2pIp;
  private String name;
  private Boolean validator;
  private Boolean bootnode;
  private Boolean apis;
  private ConfigNode parent;

  public Node(
      @JsonProperty("name") final String name,
      @JsonProperty("validator") final Boolean validator,
      @JsonProperty("bootnode") final Boolean bootnode,
      @JsonProperty("apis") final Boolean apis) {

    this.name = name;
    this.validator = requireNonNullElse(validator, false);
    this.bootnode = requireNonNullElse(bootnode, false);
    this.apis = requireNonNullElse(apis, false);

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

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public Boolean getApis() {
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
    final int p2pPort = P2P.getPort(getSiblings(), this);
    return EnodeURL.builder()
        .nodeId(keyPair.getPublicKey().getEncodedBytes())
        .ipAddress(p2pIp)
        .listeningPort(p2pPort)
        .discoveryPort(p2pPort)
        .build();
  }

  @JsonIgnore
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

    // TODO generate bash script to run the node with config file option.

    LOG.debug("Node {} address is {}", name, address);

    return nodeDir;
  }

  private void createConfigFile(final Path nodeDir) {

    NodeConfig nodeConfig = new NodeConfig();
    nodeConfig
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
        .addOption("p2p-host", "127.0.0.1")
        .addComment("P2P listening port")
        .addOption(P2P.getKey(), P2P.getPort(getSiblings(), this))
        .addComment("P2P listening interface")
        .addOption("p2p-interface", "0.0.0.0")
        .addComment("P2P hosts whitelist")
        .addOption("host-whitelist", new String[] {"all"});

    if(apis) {
      // Write RPC port option
      nodeConfig.addEmptyLine()
          .addComment("RPC/GraphQL options")

          // RPC HTTP
          .addOption("rpc-http-enabled", true)
          .addComment("HTTP RPC listening host")
          .addOption("rpc-http-host", "127.0.0.1")
          .addComment("HTTP RPC listening port")
          .addOption(RPC_HTTP.getKey(), RPC_HTTP.getPort(getSiblings(), this))
          .addComment("HTTP RPC activated APIs")
          .addOption("rpc-http-api", new String[]{"ETH", "NET", "WEB3"})
          .addComment("HTTP RPC cors")
          .addOption("rpc-http-cors-origins", new String[]{"all"})

          // RPC WebSocket
          .addEmptyLine()
          .addOption("rpc-ws-enabled", true)
          .addComment("Websocket RPC listening host")
          .addOption("rpc-ws-host", "127.0.0.1")
          .addComment("Websocket RPC listening port")
          .addOption(RPC_WS.getKey(), RPC_WS.getPort(getSiblings(), this))
          .addComment("Websocket RPC activated APIs")
          .addOption("rpc-ws-api", new String[]{"ETH", "NET", "WEB3"})

          // GraphQL
          .addEmptyLine()
          .addOption("graphql-http-enabled", true)
          .addComment("GraphQL listening host")
          .addOption("graphql-http-host", "127.0.0.1")
          .addComment("GraphQL listening port")
          .addOption(GRAPHQL.getKey(), GRAPHQL.getPort(getSiblings(), this))
          .addComment("GraphQL cors")
          .addOption("graphql-http-cors-origins", new String[]{"all"})

          // Metrics
          .addEmptyLine()
          .addComment("Metrics options")
          .addOption("metrics-enabled", true)
          .addComment("Metrics listening host")
          .addOption("metrics-host", "127.0.0.1")
          .addComment("Metrics listening port")
          .addOption(METRICS.getKey(), METRICS.getPort(getSiblings(), this));
    }

    // Write the file
    nodeConfig.write(nodeDir);

    // TODO customise TOML values
    // TODO privacy config
    //      ## Privacy
    // #privacy-url="http://127.0.0.1:8888"
    // #privacy-public-key-file="./pubKey.pub"
    // #privacy-enabled=false
    // #privacy-precompiled-address=9
    // #privacy-marker-transaction-signing-key-file="./signerKey"
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
