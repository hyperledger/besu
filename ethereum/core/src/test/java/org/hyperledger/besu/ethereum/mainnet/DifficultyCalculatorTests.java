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
 *
 */

package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.evm.internal.JumpDestCacheConfiguration;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DifficultyCalculatorTests {

  private final String testFile;
  private final ProtocolSchedule protocolSchedule;

  public DifficultyCalculatorTests(final String testFile, final ProtocolSchedule protocolSchedule) {
    this.testFile = testFile;
    this.protocolSchedule = protocolSchedule;
  }

  @Parameters(name = "TestFile: {0}")
  public static Collection<Object[]> getTestParametersForConfig() throws IOException {
    return List.of(
        new Object[] {
          "/BasicTests/difficultyMainNetwork.json",
          MainnetProtocolSchedule.fromConfig(
              GenesisConfigFile.mainnet().getConfigOptions(),
              JumpDestCacheConfiguration.DEFAULT_CONFIG)
        },
        new Object[] {
          "/BasicTests/difficultyRopsten.json",
          MainnetProtocolSchedule.fromConfig(
              GenesisConfigFile.fromConfig(
                      Resources.toString(
                          GenesisConfigFile.class.getResource("/ropsten.json"),
                          StandardCharsets.UTF_8))
                  .getConfigOptions(),
              JumpDestCacheConfiguration.DEFAULT_CONFIG)
        },
        new Object[] {
          "/BasicTests/difficultyFrontier.json",
          MainnetProtocolSchedule.fromConfig(
              GenesisConfigFile.fromConfig("{\"config\": {\"frontierBlock\":0}}")
                  .getConfigOptions(),
              JumpDestCacheConfiguration.DEFAULT_CONFIG)
        },
        new Object[] {
          "/BasicTests/difficultyHomestead.json",
          MainnetProtocolSchedule.fromConfig(
              GenesisConfigFile.fromConfig("{\"config\": {\"homesteadBlock\":0}}")
                  .getConfigOptions(),
              JumpDestCacheConfiguration.DEFAULT_CONFIG)
        },
        new Object[] {
          "/BasicTests/difficultyByzantium.json",
          MainnetProtocolSchedule.fromConfig(
              GenesisConfigFile.fromConfig("{\"config\": {\"byzantiumBlock\":0}}")
                  .getConfigOptions(),
              JumpDestCacheConfiguration.DEFAULT_CONFIG)
        },
        new Object[] {
          "/BasicTests/difficultyConstantinople.json",
          MainnetProtocolSchedule.fromConfig(
              GenesisConfigFile.fromConfig("{\"config\": {\"constantinopleBlock\":0}}")
                  .getConfigOptions(),
              JumpDestCacheConfiguration.DEFAULT_CONFIG)
        },
        new Object[] {
          "/BasicTests/difficultyEIP2384.json",
          MainnetProtocolSchedule.fromConfig(
              GenesisConfigFile.fromConfig("{\"config\":{\"muirGlacierBlock\":0}}")
                  .getConfigOptions(),
              JumpDestCacheConfiguration.DEFAULT_CONFIG)
        },
        new Object[] {
          "/BasicTests/difficultyEIP2384_random.json",
          MainnetProtocolSchedule.fromConfig(
              GenesisConfigFile.fromConfig("{\"config\":{\"muirGlacierBlock\":0}}")
                  .getConfigOptions(),
              JumpDestCacheConfiguration.DEFAULT_CONFIG)
        },
        new Object[] {
          "/BasicTests/difficultyEIP2384_random_to20M.json",
          MainnetProtocolSchedule.fromConfig(
              GenesisConfigFile.fromConfig("{\"config\":{\"muirGlacierBlock\":0}}")
                  .getConfigOptions(),
              JumpDestCacheConfiguration.DEFAULT_CONFIG)
        });
  }

  @Test
  public void testDifficultyCalculation() throws IOException {
    MainnetBlockHeaderFunctions blockHeaderFunctions = new MainnetBlockHeaderFunctions();
    final ObjectNode testObject =
        JsonUtil.objectNodeFromString(
            Resources.toString(
                DifficultyCalculatorTests.class.getResource(testFile), StandardCharsets.UTF_8));
    final var fields = testObject.fields();
    while (fields.hasNext()) {
      final var entry = fields.next();
      final JsonNode value = entry.getValue();
      final long currentBlockNumber = extractLong(value, "currentBlockNumber");
      final BlockHeader testHeader =
          BlockHeaderBuilder.create()
              .parentHash(Hash.EMPTY)
              .coinbase(Address.ZERO)
              .gasLimit(Long.MAX_VALUE)
              .stateRoot(Hash.EMPTY)
              .transactionsRoot(Hash.EMPTY)
              .receiptsRoot(Hash.EMPTY)
              .logsBloom(new LogsBloomFilter())
              .gasUsed(0)
              .extraData(Bytes.of())
              .mixHash(Hash.EMPTY)
              .nonce(0)
              .blockHeaderFunctions(blockHeaderFunctions)
              .timestamp(extractLong(value, "parentTimestamp"))
              .difficulty(Difficulty.fromHexString(value.get("parentDifficulty").asText()))
              .ommersHash(Hash.fromHexString(value.get("parentUncles").asText()))
              .number(currentBlockNumber)
              .buildBlockHeader();
      final long currentTime = extractLong(value, "currentTimestamp");
      final UInt256 currentDifficulty =
          UInt256.fromHexString(value.get("currentDifficulty").asText());
      final var spec = protocolSchedule.getByBlockNumber(currentBlockNumber);
      final var calculator = spec.getDifficultyCalculator();
      assertThat(UInt256.valueOf(calculator.nextDifficulty(currentTime, testHeader, null)))
          .describedAs("File %s Test %s", testFile, entry.getKey())
          .isEqualTo(currentDifficulty);
    }
  }

  private long extractLong(final JsonNode node, final String name) {
    return Long.decode(node.get(name).asText());
  }
}
