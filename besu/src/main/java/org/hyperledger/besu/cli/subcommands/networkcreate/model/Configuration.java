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
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import org.hyperledger.besu.cli.subcommands.networkcreate.generate.DirectoryHandler;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Generatable;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Verifiable;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.InitConfigurationErrorHandler;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO Handle errors
public class Configuration implements Verifiable, Generatable, ConfigNode {

  private static final Logger LOG = LogManager.getLogger();

  private static final String CONFIGURATION_VERSION = "1.0";

  private static final String README_FILENAME = "README.md";
  private static final String README_FILE_SOURCE = "networkcreator/README.md";
  private final String version;
  private final Network network;
  private final List<Account> accounts;
  private final Permissioning permissioning;
  private final Privacy privacy;
  private final List<Node> nodes;
  private final List<Generatable> itemsToGenerate = new ArrayList<>();
  private final List<Verifiable> itemsToVerify = new ArrayList<>();

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
    if (!isNull(this.permissioning)) this.permissioning.setParent(this);
    if (!isNull(this.privacy)) this.privacy.setParent(this);
    this.nodes.forEach(node -> node.setParent(this));

    itemsToVerify.add(network);
    itemsToVerify.addAll(nodes);
    itemsToVerify.addAll(accounts);

    itemsToGenerate.add(network);
    itemsToGenerate.addAll(nodes);
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
    if (isNull(version)) {
      errorHandler.add("Configuration version", "null", "Version not defined.");
    } else if (!CONFIGURATION_VERSION.equals(version)) {
      errorHandler.add(
          "Configuration version",
          version,
          String.format(
              "Incompatible configuration version, expected version should be %1$s.",
              CONFIGURATION_VERSION));
    }
    itemsToVerify.forEach(verifiable -> verifiable.verify(errorHandler));
    return errorHandler;
  }

  @Override
  public Path generate(
      final Path outputDirectoryPath,
      final DirectoryHandler directoryHandler,
      @Nullable final Node node) {

    final Path mainDirectory =
        outputDirectoryPath.resolve(directoryHandler.getSafeName(network.getName()));
    directoryHandler.create(mainDirectory);

    try {
      final URL readmeFileSource = getClass().getClassLoader().getResource(README_FILE_SOURCE);
      checkNotNull(readmeFileSource, "Readme template not found.");
      Files.copy(Path.of(readmeFileSource.toURI()), mainDirectory.resolve(README_FILENAME));
    } catch (IOException e) {
      LOG.warn("Unable to write readme file", e);
    } catch (URISyntaxException | NullPointerException e) {
      LOG.warn("Unable to read readme template file", e);
    }

    itemsToGenerate.forEach(
        generatable -> generatable.generate(mainDirectory, directoryHandler, null));

    return mainDirectory;
  }

  @Override
  public void setParent(final ConfigNode parent) {
    // no parent to set for this node as it's the root
    throw new RuntimeException("Root configuration node can't have a parent");
  }

  @Override
  public ConfigNode getParent() {
    // no parent to return for this node as it's the root
    return null;
  }
}
