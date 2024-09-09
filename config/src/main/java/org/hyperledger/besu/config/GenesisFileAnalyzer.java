package org.hyperledger.besu.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenesisFileAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(GenesisFileAnalyzer.class);

    record GenesisAnalysis(FileStyle fileStyle,
                                  ConsensusMechanism consensusMechanism,
                                  ObjectNode configSection, String networkVersion) {
        public enum FileStyle {BESU, GETH, UNKNOWN}

        public enum ConsensusMechanism {ETHASH, CLIQUE, IBFT2, QBFT, UNKNOWN}
    }

    static GenesisAnalysis analyzeGenesisFile(final ObjectNode genesisJson) {
        try {
            LOG.info("Starting genesis file analysis.");

            GenesisAnalysis.FileStyle fileStyle = detectFileStyle(genesisJson);
            LOG.info("Detected file style: {}", fileStyle);

            ObjectNode configSection = extractConfigSection(genesisJson, fileStyle);
            LOG.info("Config section extracted.");

            GenesisAnalysis.ConsensusMechanism consensusMechanism = detectConsensusMechanism(configSection, fileStyle);
            LOG.info("Consensus mechanism detected: {}", consensusMechanism);

            String networkVersion = detectNetworkVersion(configSection);
            LOG.info("Network version detected: {}", networkVersion);

            GenesisAnalysis analysis = new GenesisAnalysis(fileStyle, consensusMechanism, configSection, networkVersion);
            validateConsistency(analysis);

            return analysis;
        } catch (Exception e) {
            LOG.error("Error during genesis file analysis: ", e);
            throw e;
        }
    }

    private static GenesisAnalysis.FileStyle detectFileStyle(final ObjectNode genesisJson) {
        boolean hasConfig = genesisJson.has("config");
        boolean hasAllocAtRoot = genesisJson.has("alloc");
        boolean hasDifficultyAtRoot = genesisJson.has("difficulty");
        boolean hasGasLimitAtRoot = genesisJson.has("gasLimit");

        if (hasConfig && !hasAllocAtRoot && !hasDifficultyAtRoot && !hasGasLimitAtRoot) {
            return GenesisAnalysis.FileStyle.BESU;
        } else if (hasAllocAtRoot && hasDifficultyAtRoot && hasGasLimitAtRoot) {
            return GenesisAnalysis.FileStyle.GETH;
        }

        return GenesisAnalysis.FileStyle.UNKNOWN;
    }

    private static ObjectNode extractConfigSection(final ObjectNode genesisJson, final GenesisAnalysis.FileStyle fileStyle) {
        ObjectNode config = JsonUtil.createEmptyObjectNode();
        if (fileStyle == GenesisAnalysis.FileStyle.BESU && genesisJson.has("config")) {
            return (ObjectNode) genesisJson.get("config");
        } else if (fileStyle == GenesisAnalysis.FileStyle.GETH) {
            String[] relevantFields = {
                    "chainId", "homesteadBlock", "eip150Block", "eip155Block", "eip158Block",
                    "byzantiumBlock", "constantinopleBlock", "petersburgBlock", "istanbulBlock",
                    "muirGlacierBlock", "berlinBlock", "londonBlock", "clique", "ethash",
                    "difficulty", "gasLimit", "extraData"
            };
            for (String field : relevantFields) {
                if (genesisJson.has(field)) {
                    config.set(field, genesisJson.get(field));
                }
            }
            // If the config is at the root level for Geth, move it to the config object
            if (genesisJson.has("config")) {
                config.setAll((ObjectNode) genesisJson.get("config"));
            }
        }
        return config;
    }

    private static GenesisAnalysis.ConsensusMechanism detectConsensusMechanism(final ObjectNode config, final GenesisAnalysis.FileStyle fileStyle) {
        if (config.has("clique")) {
            return GenesisAnalysis.ConsensusMechanism.CLIQUE;
        } else if (config.has("ethash")) {
            return GenesisAnalysis.ConsensusMechanism.ETHASH;
        } else if (config.has("ibft2")) {
            return GenesisAnalysis.ConsensusMechanism.IBFT2;
        } else if (config.has("qbft")) {
            return GenesisAnalysis.ConsensusMechanism.QBFT;
        }
        // If no consensus mechanism is found in the config, check the file style
        if (fileStyle == GenesisAnalysis.FileStyle.GETH) {
            // For Geth-style files, default to Ethash if not specified
            return GenesisAnalysis.ConsensusMechanism.ETHASH;
        }
        return GenesisAnalysis.ConsensusMechanism.UNKNOWN;
    }

    private static String detectNetworkVersion(final ObjectNode config) {
        if (config.has("chainId")) {
            JsonNode chainIdNode = config.get("chainId");
            long chainId;
            try {
                if (chainIdNode.isTextual()) {
                    chainId = Long.parseLong(chainIdNode.asText().replaceFirst("^0x", ""), 16);
                } else if (chainIdNode.isNumber()) {
                    chainId = chainIdNode.asLong();
                } else {
                    throw new IllegalArgumentException("Unexpected chainId format");
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid chainId format", e);
            }

            return switch ((int) chainId) {
                case 1 -> "Mainnet";
                case 3 -> "Ropsten";
                case 4 -> "Rinkeby";
                case 5 -> "Goerli";
                case 42 -> "Kovan";
                default -> "Unknown network (Chain ID: " + chainId + ")";
            };
        }
        return "Unknown network";
    }

    private static void validateConsistency(final GenesisAnalysis analysis) {
        if (analysis.fileStyle() == GenesisAnalysis.FileStyle.GETH &&
                (analysis.consensusMechanism() == GenesisAnalysis.ConsensusMechanism.IBFT2 ||
                        analysis.consensusMechanism() == GenesisAnalysis.ConsensusMechanism.QBFT)) {
            LOG.warn("Inconsistent genesis file: Geth-style file with IBFT2/QBFT consensus detected. " +
                    "This combination is unusual and may indicate a misconfiguration.");
        }
        // Add more consistency checks as needed
    }
}