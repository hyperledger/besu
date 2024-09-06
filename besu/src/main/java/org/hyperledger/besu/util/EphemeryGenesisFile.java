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

import org.hyperledger.besu.config.GenesisConfigFile;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * The Generate Ephemery Genesis File. Checks for update based on the set period and update the
 * Ephemery genesis file in memory
 */
public class EphemeryGenesisFile {
  private static final int PERIOD = 28;
  private static final long PERIOD_IN_SECONDS = (PERIOD * 24 * 60 * 60);

  public static GenesisConfigFile updateGenesis(final Map<String, String> overrides)
      throws RuntimeException {
    GenesisConfigFile genesisConfigFile;
    try {
      if (EPHEMERY.getGenesisFile() == null) {
        throw new IOException("Genesis file or config options are null");
      }
      genesisConfigFile = GenesisConfigFile.fromResource(EPHEMERY.getGenesisFile());
      long genesisTimestamp = genesisConfigFile.getTimestamp();
      Optional<BigInteger> genesisChainId = genesisConfigFile.getConfigOptions().getChainId();
      long currentTimestamp = Instant.now().getEpochSecond();
      long periodsSinceGenesis =
          ChronoUnit.DAYS.between(Instant.ofEpochSecond(genesisTimestamp), Instant.now()) / PERIOD;

      long updatedTimestamp = genesisTimestamp + (periodsSinceGenesis * PERIOD_IN_SECONDS);
      BigInteger updatedChainId =
          genesisChainId
              .orElseThrow(() -> new IllegalStateException("ChainId not present"))
              .add(BigInteger.valueOf(periodsSinceGenesis));

      EPHEMERY.setNetworkId(updatedChainId);
      if (currentTimestamp > (genesisTimestamp + PERIOD_IN_SECONDS)) {
        overrides.put("chainId", String.valueOf(updatedChainId));
        overrides.put("timestamp", String.valueOf(updatedTimestamp));
        genesisConfigFile = genesisConfigFile.withOverrides(overrides);
      }
      System.out.println("overrides" + overrides);
      return genesisConfigFile.withOverrides(overrides);
    } catch (IOException e) {
      throw new RuntimeException("Error updating genesis file: " + e.getMessage(), e);
    }
  }
}
