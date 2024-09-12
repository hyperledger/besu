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
package org.hyperledger.besu.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Processes genesis files, converting them to the appropriate format if necessary. */
public class GenesisFileHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GenesisFileHandler.class);

  private GenesisFileHandler() {}

  static GenesisReader processGenesisFile(final ObjectNode config) {
    GenesisFileAnalyzer.GenesisAnalysis analysis = GenesisFileAnalyzer.analyzeGenesisFile(config);

    // Log the analysis results
    LOG.info("Genesis file analysis results:");
    LOG.info("File Style: {}", analysis.fileStyle());
    LOG.info("Consensus Mechanism: {}", analysis.consensusMechanism());
    LOG.info("Network Version: {}", analysis.networkVersion());

    // Create and return the appropriate GenesisReader
    return createGenesisReader(config, analysis);
  }

  private static GenesisReader createGenesisReader(
      final ObjectNode config, final GenesisFileAnalyzer.GenesisAnalysis analysis) {
    ObjectNode processedConfig = JsonUtil.normalizeKeys(config);

    if (analysis.fileStyle() == GenesisFileAnalyzer.GenesisAnalysis.FileStyle.GETH) {
      LOG.info("Converting Geth-style genesis file to Besu format");
      processedConfig = GenesisFileConverter.convertGethToBesu(processedConfig);
    }

    return new GenesisReader.FromObjectNode(processedConfig);
  }
}
