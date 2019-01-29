/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.cli;

import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import picocli.CommandLine;

interface DefaultCommandValues {
  String CONFIG_FILE_OPTION_NAME = "--config-file";

  String MANDATORY_PATH_FORMAT_HELP = "<PATH>";
  String MANDATORY_FILE_FORMAT_HELP = "<FILE>";
  String PANTHEON_HOME_PROPERTY_NAME = "pantheon.home";
  String DEFAULT_DATA_DIR_PATH = "./build/data";
  String MANDATORY_INTEGER_FORMAT_HELP = "<INTEGER>";
  String MANDATORY_MODE_FORMAT_HELP = "<MODE>";
  String MANDATORY_NETWORK_FORMAT_HELP = "<NETWORK>";
  String MANDATORY_NODE_ID_FORMAT_HELP = "<NODEID>";
  Wei DEFAULT_MIN_TRANSACTION_GAS_PRICE = Wei.of(1000);
  BytesValue DEFAULT_EXTRA_DATA = BytesValue.EMPTY;
  long DEFAULT_MAX_REFRESH_DELAY = 3600000;
  long DEFAULT_MIN_REFRESH_DELAY = 1;
  String DOCKER_GENESIS_LOCATION = "/etc/pantheon/genesis.json";
  String PERMISSIONING_CONFIG_LOCATION = "/etc/pantheon/permissioned_config.toml";
  String DOCKER_DATADIR_LOCATION = "/var/lib/pantheon";
  String MANDATORY_HOST_FORMAT_HELP = "<HOST>";
  String MANDATORY_PORT_FORMAT_HELP = "<PORT>";
  // Default should be FAST for the next release
  // but we use FULL for the moment as Fast is still in progress
  SyncMode DEFAULT_SYNC_MODE = SyncMode.FULL;
  int DEFAULT_MAX_PEERS = 25;
  int MAX_TRAILING_PEERS = Integer.MAX_VALUE;

  static Path getDefaultPantheonDataPath(final Object command) {
    // this property is retrieved from Gradle tasks or Pantheon running shell script.
    final String pantheonHomeProperty = System.getProperty(PANTHEON_HOME_PROPERTY_NAME);
    final Path pantheonHome;

    // If prop is found, then use it
    if (pantheonHomeProperty != null) {
      try {
        pantheonHome = Paths.get(pantheonHomeProperty);
      } catch (final InvalidPathException e) {
        throw new CommandLine.ParameterException(
            new CommandLine(command),
            String.format(
                "Unable to define default data directory from %s property.",
                PANTHEON_HOME_PROPERTY_NAME),
            e);
      }
    } else {
      // otherwise use a default path.
      // That may only be used when NOT run from distribution script and Gradle as they all define
      // the property.
      try {
        final String path = new File(DEFAULT_DATA_DIR_PATH).getCanonicalPath();
        pantheonHome = Paths.get(path);
      } catch (final IOException e) {
        throw new CommandLine.ParameterException(
            new CommandLine(command), "Unable to create default data directory.");
      }
    }

    // Try to create it, then verify if the provided path is not already existing and is not a
    // directory .Otherwise, if it doesn't exist or exists but is already a directory,
    // Runner will use it to store data.
    try {
      Files.createDirectories(pantheonHome);
    } catch (final FileAlreadyExistsException e) {
      // Only thrown if it exist but is not a directory
      throw new CommandLine.ParameterException(
          new CommandLine(command),
          String.format(
              "%s: already exists and is not a directory.", pantheonHome.toAbsolutePath()),
          e);
    } catch (final Exception e) {
      throw new CommandLine.ParameterException(
          new CommandLine(command),
          String.format("Error creating directory %s.", pantheonHome.toAbsolutePath()),
          e);
    }
    return pantheonHome;
  }
}
