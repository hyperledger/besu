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
package org.hyperledger.besu.cli.subcommands.networkcreate;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createTempDirectory;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.cli.CommandTestAbstract;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.tuweni.toml.Toml;
import org.junit.Before;
import org.junit.Test;
import picocli.CommandLine.Model.CommandSpec;

public class NetworkCreateSubCommandTest extends CommandTestAbstract {

  private Path tmpOutputDirectoryPath;

  @Before
  public void init() throws IOException {
    tmpOutputDirectoryPath = createTempDirectory(format("output-%d", currentTimeMillis()));
    Configurator.setAllLevels("", Level.ALL);
  }

  @Test
  public void subCommandExists() {
    final CommandSpec spec = parseCommand().getSpec();
    assertThat(spec.subcommands()).containsKeys("network-create");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void subCommandHelpIsDisplayed() {
    parseCommand("network-create", "--help");

    assertThat(commandOutput.toString())
        .contains(
            "Network Creator subcommand intakes an initialization file and outputs the\n"
                + "network genesis file, pre-configured nodes and keys.");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void subCommandCreatesNetworkSetupFromToml() throws Exception {
    final File tempConfigFile = temp.newFile("init.toml");
    Files.copy(
        Path.of(this.getClass().getResource("/networkcreate/test.toml").toURI()),
        tempConfigFile.toPath(),
        StandardCopyOption.REPLACE_EXISTING);

    final List<String> args = generateArgs(tempConfigFile, tmpOutputDirectoryPath);
    testGenerateNetworkConfig(args);
  }

  @Test
  public void subCommandCreatesNetworkSetupFromJson() throws Exception {
    final File tempConfigFile = temp.newFile("init.json");

    // use TOML File as unique source and transform it to required format, here JSON.
    final String jsonString =
        Toml.parse(Path.of(this.getClass().getResource("/networkcreate/test.toml").toURI()))
            .toJson();
    try (final BufferedWriter fileWriter =
        Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8)) {
      fileWriter.write(jsonString);
      fileWriter.flush();
      final List<String> args = generateArgs(tempConfigFile, tmpOutputDirectoryPath);
      testGenerateNetworkConfig(args);
    }
  }

  @Test
  public void subCommandCreatesNetworkSetupFromYaml() throws Exception {
    final File tempConfigFile = temp.newFile("init.yaml");

    // use TOML File as unique source and transform it to required format, here YAML (via JSON)
    final String jsonString =
        Toml.parse(Path.of(this.getClass().getResource("/networkcreate/test.toml").toURI()))
            .toJson();
    final JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
    final String yamlString = new YAMLMapper().writeValueAsString(jsonNodeTree);

    try (final BufferedWriter fileWriter =
        Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8)) {
      fileWriter.write(yamlString);
      fileWriter.flush();
      final List<String> args = generateArgs(tempConfigFile, tmpOutputDirectoryPath);
      testGenerateNetworkConfig(args);
    }
  }

  private void testGenerateNetworkConfig(final List<String> args) {

    parseCommand(args.toArray(new String[0]));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    final String NETWORK_DIR_NAME = "network_1";
    final String[] NODES_DIR_NAMES = {"node_1", "node_2", "node_3", "node_4"};

    assertThat(tmpOutputDirectoryPath.resolve(NETWORK_DIR_NAME)).exists().isDirectory();

    assertThat(tmpOutputDirectoryPath.resolve(NETWORK_DIR_NAME).resolve("genesis.json"))
        .exists()
        .isRegularFile();

    assertThat(tmpOutputDirectoryPath.resolve(NETWORK_DIR_NAME).resolve("README.md"))
        .exists()
        .isRegularFile();

    for (String nodeDirName : NODES_DIR_NAMES) {
      assertThat(tmpOutputDirectoryPath.resolve(NETWORK_DIR_NAME).resolve(nodeDirName))
          .exists()
          .isDirectory();
      assertThat(
              tmpOutputDirectoryPath
                  .resolve(NETWORK_DIR_NAME)
                  .resolve(nodeDirName)
                  .resolve("config.toml"))
          .exists()
          .isRegularFile();
      assertThat(
              tmpOutputDirectoryPath.resolve(NETWORK_DIR_NAME).resolve(nodeDirName).resolve("key"))
          .exists()
          .isRegularFile();
    }
  }

  private List<String> generateArgs(final File tempConfigFile, final Path outputDirectoryPath) {
    final List<String> args = new ArrayList<>();

    args.add("--data-path"); // We force data path to be able to assert existence of the  resulting
    // directory structure and files
    args.add(tmpOutputDirectoryPath.toAbsolutePath().toString());

    args.add("network-create"); // sub command to test

    args.add("--initialization-file"); // init file source option
    args.add(tempConfigFile.getPath()); // init file to use as source

    args.add("--to"); // target dir option
    args.add(outputDirectoryPath.toString()); // target dir path

    return args;
  }
}
