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
package org.hyperledger.besu.cli.subcommands.networkcreate.model;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import org.hyperledger.besu.cli.subcommands.networkcreate.generate.DirectoryHandler;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Generatable;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Verifiable;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.InitConfigurationErrorHandler;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

// TODO Handle errors
public class Configuration implements Verifiable, Generatable, ConfigNode {
  private final String version;
  private final Network network;
  private final List<Account> accounts;
  private final Permissioning permissioning;
  private final Privacy privacy;
  private final List<Node> nodes;

  @JsonCreator(mode = Mode.PROPERTIES)
  public Configuration(
      @JsonProperty("version") final String version,
      @JsonProperty("network") final Network network,
      @JsonProperty("accounts") final List<Account> accounts,
      @JsonProperty("permissioning") final Permissioning permissioning,
      @JsonProperty("privacy") final Privacy privacy,
      @JsonProperty("nodes") final List<Node> nodes) {

    this.version = version;
    this.network = requireNonNull(network, "Network not defined.");
    this.nodes = nodes;
    this.permissioning = permissioning;
    this.privacy = privacy;
    this.accounts = accounts;

    this.network.setAccounts(accounts);
    this.network
        .getConsensus()
        .setValidators(nodes.stream().filter(Node::getValidator).collect(Collectors.toList()));

    // setting parent config nodes
    this.network.setParent(this);
    this.accounts.forEach(account -> account.setParent(this));
    if(!isNull(this.permissioning))this.permissioning.setParent(this);
    if(!isNull(this.privacy))this.privacy.setParent(this);
    this.nodes.forEach(node -> node.setParent(this));
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public String getVersion() {
    return version;
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public Network getNetwork() {
    return network;
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  @JsonInclude(Include.NON_ABSENT)
  public Optional<List<Account>> getAccounts() {
    return Optional.ofNullable(accounts);
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  @JsonInclude(Include.NON_ABSENT)
  public Optional<Permissioning> getPermissioning() {
    return Optional.ofNullable(permissioning);
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  @JsonInclude(Include.NON_ABSENT)
  public Optional<Privacy> getPrivacy() {
    return Optional.ofNullable(privacy);
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public List<Node> getNodes() {
    return nodes;
  }

  // TODO Handle errors and verify each config node
  @Override
  public InitConfigurationErrorHandler verify(final InitConfigurationErrorHandler errorHandler) {
    if (isNull(version)) errorHandler.add("Configuration version", "null", "Version not defined.");
    this.network.verify(errorHandler);
//    this.accounts.forEach(account -> account.verify(errorHandler));
//    if(!isNull(this.permissioning))this.permissioning.verify(errorHandler);
//    if(!isNull(this.privacy))this.privacy.verify(errorHandler);
//    this.nodes.forEach(node -> node.verify(errorHandler));

    if (!errorHandler.getErrors().isEmpty()) System.out.println(errorHandler);
    return errorHandler;
  }

  @Override
  public Path generate(final Path outputDirectoryPath) {
    final DirectoryHandler directoryHandler = new DirectoryHandler();

    final Path mainDirectory =
        outputDirectoryPath.resolve(directoryHandler.getSafeName(network.getName()));
    directoryHandler.create(mainDirectory);

    network.generate(mainDirectory);

    nodes.forEach(node -> node.generate(mainDirectory));

    return mainDirectory;
  }

  @Override
  public void setParent(ConfigNode parent) {
    // no parent to set for this node as it's the root
    throw new RuntimeException("Root configuration node can't have a parent");
  }

  @Override
  public ConfigNode getParent() {
    // no parent to return for this node as it's the root
    return null;
  }
}
