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

package org.hyperledger.besu.ethereum.core;

import static org.hyperledger.besu.config.JsonUtil.normalizeKeys;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.JsonGenesisConfigOptions;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ProtocolScheduleFixture {
  public static final ProtocolSchedule MAINNET =
      MainnetProtocolSchedule.fromConfig(
          getMainnetConfigOptions(), PrivacyParameters.DEFAULT, false, EvmConfiguration.DEFAULT);

  private static GenesisConfigOptions getMainnetConfigOptions() {
    // this method avoids reading all the alloc accounts when all we want is the "config" section
    try (final JsonParser jsonParser =
        new JsonFactory().createParser(GenesisConfigFile.class.getResource("/mainnet.json"))) {

      while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
        if ("config".equals(jsonParser.getCurrentName())) {
          jsonParser.nextToken();
          return JsonGenesisConfigOptions.fromJsonObject(
              normalizeKeys(new ObjectMapper().readTree(jsonParser)));
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed open or parse mainnet genesis json", e);
    }
    throw new IllegalArgumentException("mainnet json file had no config section");
  }
}
