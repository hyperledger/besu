/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.permissioning;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.permissioning.LocalPermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.SmartContractPermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.WhitelistPersistor;
import tech.pegasys.pantheon.ethereum.permissioning.WhitelistPersistor.WHITELIST_TYPE;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.RunnableNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.PantheonNodeConfigurationBuilder;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.PantheonNodeFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.assertj.core.util.Lists;

public class PermissionedNodeBuilder {

  private String name;
  private String genesisFile;

  private boolean localConfigNodesPermissioningEnabled = false;
  private Path localConfigNodesPermissioningFile = null;
  private Collection<URI> localConfigPermittedNodes = null;

  private boolean localConfigAccountsPermissioningEnabled = false;
  private Path localConfigAccountsPermissioningFile = null;
  private Collection<String> localConfigPermittedAccounts = null;

  private boolean nodeSmartContractPermissioningEnabled = false;
  private String nodePermissioningSmartContractAddress = null;

  private boolean accountSmartContractPermissioningEnabled = false;
  private String accountPermissioningSmartContractAddress = null;

  private List<String> staticNodes = new ArrayList<>();
  private boolean mining = true;

  public PermissionedNodeBuilder name(final String name) {
    this.name = name;
    return this;
  }

  public PermissionedNodeBuilder nodesConfigFileEnabled() {
    this.localConfigNodesPermissioningEnabled = true;
    if (this.localConfigPermittedNodes == null) {
      this.localConfigPermittedNodes = new ArrayList<>();
    }
    return this;
  }

  public PermissionedNodeBuilder nodesConfigFile(final Path file) {
    this.localConfigNodesPermissioningFile = file;
    return this;
  }

  public PermissionedNodeBuilder nodesPermittedInConfig(final List<URI> nodes) {
    this.localConfigNodesPermissioningEnabled = true;
    this.localConfigPermittedNodes = new ArrayList<>(nodes);
    return this;
  }

  public PermissionedNodeBuilder nodesPermittedInConfig(final Node... nodes) {
    this.localConfigNodesPermissioningEnabled = true;
    this.localConfigPermittedNodes = new ArrayList<>(convertToEnodes(Lists.newArrayList(nodes)));
    return this;
  }

  public PermissionedNodeBuilder accountsConfigFileEnabled() {
    this.localConfigAccountsPermissioningEnabled = true;
    if (this.localConfigPermittedAccounts == null) {
      this.localConfigPermittedAccounts = new ArrayList<>();
    }
    return this;
  }

  public PermissionedNodeBuilder accountsConfigFile(final Path file) {
    this.localConfigAccountsPermissioningFile = file;
    return this;
  }

  public PermissionedNodeBuilder accountsPermittedInConfig(final List<String> accounts) {
    this.localConfigAccountsPermissioningEnabled = true;
    this.localConfigPermittedAccounts = new ArrayList<>(accounts);
    return this;
  }

  public PermissionedNodeBuilder nodesContractEnabled(final String address) {
    this.nodeSmartContractPermissioningEnabled = true;
    this.nodePermissioningSmartContractAddress = address;
    return this;
  }

  public PermissionedNodeBuilder accountsContractEnabled(final String address) {
    this.accountSmartContractPermissioningEnabled = true;
    this.accountPermissioningSmartContractAddress = address;
    return this;
  }

  public PermissionedNodeBuilder staticNodes(final List<String> staticNodes) {
    this.staticNodes = staticNodes;
    return this;
  }

  public PermissionedNodeBuilder disableMining() {
    this.mining = false;
    return this;
  }

  @SuppressWarnings("UnstableApiUsage")
  public PermissionedNodeBuilder genesisFile(final String path) {
    try {
      URI uri = this.getClass().getResource(path).toURI();
      this.genesisFile = Resources.toString(uri.toURL(), Charset.defaultCharset());
    } catch (final URISyntaxException | IOException e) {
      throw new IllegalStateException("Unable to read genesis file from: " + path, e);
    }
    return this;
  }

  public PantheonNode build() {
    if (name == null) {
      name = "perm_node_" + UUID.randomUUID().toString().substring(0, 8);
    }

    Optional<LocalPermissioningConfiguration> localPermConfig = Optional.empty();
    if (localConfigNodesPermissioningEnabled || localConfigAccountsPermissioningEnabled) {
      localPermConfig = Optional.of(localConfigPermissioningConfiguration());
    }

    Optional<SmartContractPermissioningConfiguration> smartContractPermConfig = Optional.empty();
    if (nodeSmartContractPermissioningEnabled || accountSmartContractPermissioningEnabled) {
      smartContractPermConfig = Optional.of(smartContractPermissioningConfiguration());
    }

    final PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(localPermConfig, smartContractPermConfig);

    final PantheonNodeConfigurationBuilder builder = new PantheonNodeConfigurationBuilder();
    builder
        .name(name)
        .jsonRpcConfiguration(jsonRpcConfigWithPermApiEnabled())
        .permissioningConfiguration(permissioningConfiguration)
        .bootnodeEligible(false);

    if (mining) {
      builder.miningEnabled();
    }

    if (!staticNodes.isEmpty()) {
      builder.staticNodes(staticNodes);
    }

    if (genesisFile != null) {
      builder.genesisConfigProvider((a) -> Optional.of(genesisFile));
      builder.devMode(false);
    }

    try {
      return new PantheonNodeFactory().create(builder.build());
    } catch (IOException e) {
      throw new RuntimeException("Error creating PantheonNode", e);
    }
  }

  private LocalPermissioningConfiguration localConfigPermissioningConfiguration() {
    LocalPermissioningConfiguration localPermissioningConfiguration =
        LocalPermissioningConfiguration.createDefault();

    if (localConfigPermittedNodes != null) {
      if (localConfigNodesPermissioningFile == null) {
        localConfigNodesPermissioningFile = createTemporaryPermissionsFile();
      }

      List<String> nodesAsListOfStrings =
          localConfigPermittedNodes.stream().map(URI::toASCIIString).collect(Collectors.toList());
      initPermissioningConfigurationFile(
          WHITELIST_TYPE.NODES, nodesAsListOfStrings, localConfigNodesPermissioningFile);

      localPermissioningConfiguration.setNodeWhitelist(localConfigPermittedNodes);
      localPermissioningConfiguration.setNodePermissioningConfigFilePath(
          localConfigNodesPermissioningFile.toAbsolutePath().toString());
    }

    if (localConfigPermittedAccounts != null) {
      if (localConfigAccountsPermissioningFile == null) {
        localConfigAccountsPermissioningFile = createTemporaryPermissionsFile();
      }

      initPermissioningConfigurationFile(
          WHITELIST_TYPE.ACCOUNTS,
          localConfigPermittedAccounts,
          localConfigAccountsPermissioningFile);

      localPermissioningConfiguration.setAccountWhitelist(localConfigPermittedAccounts);
      localPermissioningConfiguration.setAccountPermissioningConfigFilePath(
          localConfigAccountsPermissioningFile.toAbsolutePath().toString());
    }

    return localPermissioningConfiguration;
  }

  private SmartContractPermissioningConfiguration smartContractPermissioningConfiguration() {
    SmartContractPermissioningConfiguration config =
        SmartContractPermissioningConfiguration.createDefault();
    if (nodePermissioningSmartContractAddress != null) {
      config.setNodeSmartContractAddress(
          Address.fromHexString(nodePermissioningSmartContractAddress));
      config.setSmartContractNodeWhitelistEnabled(true);
    }

    if (accountPermissioningSmartContractAddress != null) {
      config.setAccountSmartContractAddress(
          Address.fromHexString(accountPermissioningSmartContractAddress));
      config.setSmartContractAccountWhitelistEnabled(true);
    }

    return config;
  }

  private Path createTemporaryPermissionsFile() {
    final File tempFile;
    try {
      tempFile = File.createTempFile("temp", "temp");
      tempFile.deleteOnExit();
    } catch (IOException e) {
      throw new RuntimeException("Error creating temporary permissioning file", e);
    }
    return tempFile.toPath();
  }

  private JsonRpcConfiguration jsonRpcConfigWithPermApiEnabled() {
    final JsonRpcConfiguration jsonRpcConfig = JsonRpcConfiguration.createDefault();
    jsonRpcConfig.setEnabled(true);
    jsonRpcConfig.setPort(0);
    jsonRpcConfig.setHostsWhitelist(singletonList("*"));
    jsonRpcConfig.setCorsAllowedDomains(singletonList("*"));
    final List<RpcApi> rpcApis = new ArrayList<>(jsonRpcConfig.getRpcApis());
    rpcApis.add(RpcApis.PERM);
    rpcApis.add(RpcApis.ADMIN);
    jsonRpcConfig.setRpcApis(rpcApis);
    return jsonRpcConfig;
  }

  private void initPermissioningConfigurationFile(
      final WhitelistPersistor.WHITELIST_TYPE listType,
      final Collection<String> whitelistVal,
      final Path configFilePath) {
    try {
      WhitelistPersistor.addNewConfigItem(listType, whitelistVal, configFilePath);

      Files.write(
          configFilePath,
          System.lineSeparator().getBytes(Charsets.UTF_8),
          StandardOpenOption.WRITE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new RuntimeException("Error populating permissioning file", e);
    }
  }

  private List<URI> convertToEnodes(final List<Node> nodes) {
    return nodes.stream()
        .map(node -> (RunnableNode) node)
        .map(RunnableNode::enodeUrl)
        .collect(toList());
  }
}
