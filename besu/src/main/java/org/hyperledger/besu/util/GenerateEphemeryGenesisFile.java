/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.util;

import static org.hyperledger.besu.cli.config.NetworkName.EPHEMERY;

import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Generate Ephemery Genesis File. Checks for update based on the set period and update the
 * Ephemery genesis file
 */
public class GenerateEphemeryGenesisFile {
  private final NetworkName network;
  private final GenesisConfigFile genesisConfigFile;
  private final GenesisConfigOptions genesisConfigOptions;

  /**
   * Instantiates a new Generate Ephemery genesis file.
   *
   * @param network the Network
   * @param genesisConfigFile the Genesis Config File
   * @param genesisConfigOptions the Genesis Config Options
   */
  public GenerateEphemeryGenesisFile(
      final NetworkName network,
      final GenesisConfigFile genesisConfigFile,
      final GenesisConfigOptions genesisConfigOptions) {
    this.genesisConfigFile = genesisConfigFile;
    this.network = network;
    this.genesisConfigOptions = genesisConfigOptions;
  }

  public void generate() throws IOException {
    if (EPHEMERY.getGenesisFile() != null
        || genesisConfigOptions != null
        || genesisConfigFile != null) {

      final int PERIOD = 28;
      long genesisTimestamp = genesisConfigFile.getTimestamp();
      Optional<BigInteger> genesisChainId = genesisConfigOptions.getChainId();

      long periodInSeconds = (PERIOD * 24 * 60 * 60);
      long currentTimestamp = Instant.now().getEpochSecond();
      long periodsSinceGenesis =
          ChronoUnit.DAYS.between(Instant.ofEpochSecond(genesisTimestamp), Instant.now()) / PERIOD;
      long updatedTimestamp = genesisTimestamp + (periodsSinceGenesis * periodInSeconds);
      BigInteger updatedChainId =
          genesisChainId
              .orElseThrow(() -> new IllegalStateException("ChainId not present"))
              .add(BigInteger.valueOf(periodsSinceGenesis));

      EPHEMERY.setNetworkId(updatedChainId);

      if (currentTimestamp > (genesisTimestamp + periodInSeconds)) {

        GenesisConfigFile.fromResource(
            Optional.ofNullable(network).orElse(EPHEMERY).getGenesisFile());
        Path ephemeryGenesisfilePath = Path.of(EPHEMERY.getGenesisFile());
        try {
          ObjectMapper objectMapper = new ObjectMapper();
          ObjectNode rootNode =
              (ObjectNode) objectMapper.readTree(ephemeryGenesisfilePath.toFile());

          ObjectNode configNode = (ObjectNode) rootNode.path("config");
          configNode.put("chainId", updatedChainId.toString());
          rootNode.put("timestamp", String.valueOf(updatedTimestamp));

          objectMapper
              .writerWithDefaultPrettyPrinter()
              .writeValue(ephemeryGenesisfilePath.toFile(), rootNode);
        } catch (IOException e) {
          throw new IOException("Unable to update ephemery genesis file. " + e);
        }
      }
    }
  }
}
