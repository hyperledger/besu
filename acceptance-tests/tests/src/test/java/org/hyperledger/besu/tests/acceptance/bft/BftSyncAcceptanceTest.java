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
package org.hyperledger.besu.tests.acceptance.bft;

import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hyperledger.besu.config.JsonUtil;

import java.math.BigInteger;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.web3j.protocol.core.DefaultBlockParameter;

import static java.lang.Thread.sleep;

public class BftSyncAcceptanceTest extends ParameterizedBftTestBase {

  static Stream<Arguments> syncModeTestParameters() {
    return Stream.of(SyncMode.SNAP,SyncMode.FULL, SyncMode.CHECKPOINT)
        .flatMap(syncMode -> 
          factoryFunctions().map(args -> 
            Arguments.of(args.get()[0], args.get()[1], syncMode)));
  }

  @ParameterizedTest(name = "{index}: {0} with {2} sync")
  @MethodSource("syncModeTestParameters")
  public void shouldSyncNonValidatorWithSingleValidator(
      final String testName, 
      final BftAcceptanceTestParameterization nodeFactory,
      final SyncMode syncMode) throws Exception {
    setUp(testName, nodeFactory);
    
    // Create single validator network
    final String[] validators = {"validator1"};
    final BesuNode validator = 
        nodeFactory.createNodeWithValidators(besu, "validator1", validators);
    
    // Start validator and wait for more blocks
    cluster.start(validator);
    cluster.verify(blockchain.reachesHeight(validator, 20));

      // Create non-validator node with specified sync mode
      final BesuNode nonValidator =
              nodeFactory.createNodeWithValidators(besu, "non-validator-" + syncMode.toString().toLowerCase(Locale.ROOT), validators);

    // Configure genesis with the checkpoint sync settings after mining blocks
    if (syncMode == SyncMode.CHECKPOINT) {
        final Optional<String> genesisConfig = 
            validator.getGenesisConfigProvider().create(java.util.List.of(validator));
        
        if (genesisConfig.isPresent()) {
            final ObjectNode genesisConfigNode = JsonUtil.objectNodeFromString(genesisConfig.get());
            final ObjectNode config = (ObjectNode) genesisConfigNode.get("config");
            
            // Get block 10 details from validator for checkpoint configuration
            final DefaultBlockParameter blockParameter =
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(10));
            final String blockHash = validator.execute(ethTransactions.block(blockParameter)).getHash();
            
            // Add checkpoint configuration with actual block details
            final ObjectNode checkpoint = config.putObject("checkpoint");
            checkpoint.put("number", 10);
            checkpoint.put("hash", blockHash);
            
            nonValidator.setGenesisConfig(genesisConfigNode.toString());
        }
    }

    // Configure synchronizer for checkpoint sync
    final SynchronizerConfiguration syncConfig = SynchronizerConfiguration.builder()
        .syncMode(syncMode).build();
    nonValidator.setSynchronizerConfiguration(syncConfig);

    // Add node to cluster and start
    cluster.addNode(nonValidator);

    sleep(5000); // Wait for node to start

    // Verify the sync succeeds
    cluster.verify(blockchain.reachesHeight(nonValidator, 20));
  }
}