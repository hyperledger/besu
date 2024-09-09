package org.hyperledger.besu.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenesisFileValidator {
    private static final Logger LOG = LoggerFactory.getLogger(GenesisFileValidator.class);

    static GenesisReader validateAndCreateGenesisReader(final ObjectNode config) {
        GenesisFileAnalyzer.GenesisAnalysis analysis = GenesisFileAnalyzer.analyzeGenesisFile(config);

        // Log the analysis results
        LOG.info("Genesis file analysis results:");
        LOG.info("File Style: {}", analysis.fileStyle());
        LOG.info("Consensus Mechanism: {}", analysis.consensusMechanism());
        LOG.info("Network Version: {}", analysis.networkVersion());

        // Create and return the appropriate GenesisReader
        return createAppropriateGenesisReader(config, analysis);
    }

    private static GenesisReader createAppropriateGenesisReader(final ObjectNode config, final GenesisFileAnalyzer.GenesisAnalysis analysis) {
        ObjectNode processedConfig = JsonUtil.normalizeKeys(config);

        if (analysis.fileStyle() == GenesisFileAnalyzer.GenesisAnalysis.FileStyle.GETH) {
            LOG.info("Converting Geth-style genesis file to Besu format");
            processedConfig = GenesisFileConverter.convertGethToBesu(processedConfig);
        }

        // No need for additional Clique fixes here as they're now handled in the converter

        return new GenesisReader.FromObjectNode(processedConfig);
    }
}