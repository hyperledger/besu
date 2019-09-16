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
package org.hyperledger.besu.cli.subcommands.networkcreate;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

class InitConfiguration implements Verifiable, Generatable {
  private final String version;
  private final Network network;
  private final Permissioning permissioning;
  private final Privacy privacy;
  private final List<Node> nodes;

  @JsonCreator(mode = Mode.PROPERTIES)
  public InitConfiguration(
      @JsonProperty("version") final String version,
      @JsonProperty("network") final Network network,
      @JsonProperty("permissioning") final Permissioning permissioning,
      @JsonProperty("privacy") final Privacy privacy,
      @JsonProperty("nodes") final List<Node> nodes) {

    this.version = version;
    this.network = requireNonNull(network, "Network not defined.");
    this.nodes = nodes;
    this.permissioning = permissioning;
    this.privacy = privacy;

    network
        .getClique()
        .setValidators(nodes.stream().filter(Node::getValidator).collect(Collectors.toList()));
  }

  public String getVersion() {
    return version;
  }

  public Network getNetwork() {
    return network;
  }

  @JsonInclude(Include.NON_ABSENT)
  public Optional<Permissioning> getPermissioning() {
    return Optional.ofNullable(permissioning);
  }

  @JsonInclude(Include.NON_ABSENT)
  public Optional<Privacy> getPrivacy() {
    return Optional.ofNullable(privacy);
  }

  public List<Node> getNodes() {
    return nodes;
  }

  // TODO Handle errors
  @Override
  public InitConfigurationErrorHandler verify(final InitConfigurationErrorHandler errorHandler) {
    if (isNull(version)) errorHandler.add("Configuration version", "null", "Version not defined.");
    this.network.verify(errorHandler);
    if (!errorHandler.getErrors().isEmpty()) System.out.println(errorHandler);
    return errorHandler;
  }

  @Override
  public void generate(final Path outputDirectoryPath) {
    DirectoryHandler directoryHandler = new DirectoryHandler();

    Path mainDirectory =
        outputDirectoryPath.resolve(directoryHandler.getSafeName(network.getName()));

    directoryHandler.create(mainDirectory);

    network.generate(mainDirectory);

    for (Node node : nodes) {
      node.generate(mainDirectory);
    }
  }
}
