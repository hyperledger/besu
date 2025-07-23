/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.tests.acceptance.plugins;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BlockchainServicePluginTest extends AcceptanceTestBase {
  private BesuNode minerNode;
  private BesuNode pluginNode;

  @BeforeEach
  public void setUp() throws Exception {
    minerNode =
        besu.createMinerNode(
            "minerNode",
            besuNodeConfigurationBuilder -> besuNodeConfigurationBuilder.devMode(false));
    minerNode.setGenesisConfig(GENESIS_CONFIG);
    pluginNode =
        besu.createPluginsNode(
            "pluginNode",
            Collections.singletonList("testPlugins"),
            besuNodeConfigurationBuilder -> besuNodeConfigurationBuilder.devMode(false));
    pluginNode.setGenesisConfig(GENESIS_CONFIG);

    cluster.start(pluginNode, minerNode);
  }

  @Test
  public void hardforkListIsCorrect() throws IOException {
    final Path hardforkListFile = pluginNode.homeDirectory().resolve("plugins/hardfork.list");
    waitForFile(hardforkListFile);
    final var fileContents = Files.readAllLines(hardforkListFile);

    final var expectedLines = List.of("0,BERLIN,BERLIN", "1,BERLIN,LONDON", "2,LONDON,LONDON");

    for (int i = 0; i < expectedLines.size(); i++) {
      assertThat(fileContents.get(i)).isEqualTo(expectedLines.get(i));
    }
  }

  private static final String GENESIS_CONFIG =
      """
        {
          "config": {
            "chainId": 1337,
            "berlinBlock": 0,
            "londonBlock": 2,
            "contractSizeLimit": 2147483647,
            "ethash": {
              "fixeddifficulty": 100
            }
          },
          "nonce": "0x42",
          "baseFeePerGas":"0x0",
          "timestamp": "0x0",
          "extraData": "0x11bbe8db4e347b4e8c937c1c8370e4b5ed33adb3db69cbdb7a38e1e50b1b82fa",
          "gasLimit": "0x1fffffffffffff",
          "difficulty": "0x10000",
          "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
          "coinbase": "0x0000000000000000000000000000000000000000",
          "alloc": {
            "fe3b557e8fb62b89f4916b721be55ceb828dbd73": {
              "privateKey": "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
              "comment": "private key and this comment are ignored.  In a real chain, the private key should NOT be stored",
              "balance": "0xad78ebc5ac6200000"
            },
            "627306090abaB3A6e1400e9345bC60c78a8BEf57": {
              "privateKey": "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3",
              "comment": "private key and this comment are ignored.  In a real chain, the private key should NOT be stored",
              "balance": "90000000000000000000000"
            },
            "f17f52151EbEF6C7334FAD080c5704D77216b732": {
              "privateKey": "ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f",
              "comment": "private key and this comment are ignored.  In a real chain, the private key should NOT be stored",
              "balance": "90000000000000000000000"
            }
          }
        }
        """;
}
