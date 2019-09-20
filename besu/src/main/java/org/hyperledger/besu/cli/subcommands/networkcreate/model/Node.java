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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.DirectoryHandler;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Generatable;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Util;

// TODO Handle errors
class Node implements Generatable, ConfigNode {

  private static final Logger LOG = LogManager.getLogger();
  private static final String PRIVATE_KEY_FILENAME = "key";
  private static final String CONFIG_FILENAME = "config.toml";
  private static final String CONFIG_TEMPLATE_FILENAME = "/networkcreate/node-config-template.toml";

  @JsonIgnore private final KeyPair keyPair;
  @JsonIgnore private final Address address;
  private String name;
  private Boolean validator;
  private Boolean bootnode;
  private ConfigNode parent;

  public Node(
      @NonNull @JsonProperty("name") final String name,
      @Nullable @JsonProperty("validator") final Boolean validator,
      @Nullable @JsonProperty("bootnode") final Boolean bootnode) {
    this.name = requireNonNull(name, "Node name not defined.");
    this.validator = requireNonNullElse(validator, false);
    this.bootnode = requireNonNullElse(bootnode, false);

    keyPair = SECP256K1.KeyPair.generate();
    address = Util.publicKeyToAddress(keyPair.getPublicKey());
  }

  public String getName() {
    return name;
  }

  Boolean getValidator() {
    return validator;
  }

  public Boolean getBootnode() {
    return bootnode;
  }

  public KeyPair getKeyPair() {
    return keyPair;
  }

  public Address getAddress() {
    return address;
  }

  @Override
  public Path generate(final Path outputDirectoryPath) {
    final DirectoryHandler directoryHandler = new DirectoryHandler();
    final Path nodeDir = outputDirectoryPath.resolve(directoryHandler.getSafeName(name));
    directoryHandler.create(nodeDir);

    try {
      Files.write(nodeDir.resolve(PRIVATE_KEY_FILENAME), keyPair.getPrivateKey().toString().getBytes(UTF_8), StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      LOG.error("Unable to write private key file", e);
    }

    createConfigFile(nodeDir);

    LOG.debug("Node {} address is {}", name, address);

    return nodeDir;
  }

  private void createConfigFile(final Path nodeDir){
    assertThat(parent instanceof Configuration);
    //TODO get list of bootnodes
//    List<Node> siblings = ((Configuration)parent).getNodes();
//    List<Address> bootnodesEnodeAddresses = siblings.stream().filter(node -> node.bootnode)
//        .map(bootnode -> bootnode.address).collect(
//            Collectors.toList());
    //TODO customise TOML values
    try {
      final URL configTemplateFile = this.getClass().getResource(CONFIG_TEMPLATE_FILENAME);
      final String configTemplateSource = Resources.toString(configTemplateFile, UTF_8);
      Files.write(nodeDir.resolve(CONFIG_FILENAME),
          configTemplateSource.getBytes(UTF_8), StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      LOG.error("Unable to write node configuration file", e);
    }
  }

  @Override
  public void setParent(ConfigNode parent) {
    this.parent = parent;
  }

  @Override
  public ConfigNode getParent() {
    return parent;
  }
}
