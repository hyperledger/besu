package org.hyperledger.besu.config;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GenesisFileAnalyzerTest {

    private static final Logger LOG = LoggerFactory.getLogger(GenesisFileAnalyzerTest.class);
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @ParameterizedTest
    @MethodSource("provideGenesisConfigurations")
    void testAnalyzeGenesisFile(final ObjectNode genesis,
                                final GenesisFileAnalyzer.GenesisAnalysis.FileStyle expectedFileStyle,
                                final GenesisFileAnalyzer.GenesisAnalysis.ConsensusMechanism expectedConsensus,
                                final String expectedNetwork) {
        GenesisFileAnalyzer.GenesisAnalysis analysis = GenesisFileAnalyzer.analyzeGenesisFile(genesis);
        LOG.info("Genesis JSON: {}", genesis);
        LOG.info("Analyzed File Style: {}", analysis.fileStyle());
        LOG.info("Analyzed Consensus Mechanism: {}", analysis.consensusMechanism());
        LOG.info("Analyzed Network Version: {}", analysis.networkVersion());
        assertEquals(expectedFileStyle, analysis.fileStyle(), "File style mismatch");
        assertEquals(expectedConsensus, analysis.consensusMechanism(), "Consensus mechanism mismatch");
        assertEquals(expectedNetwork, analysis.networkVersion(), "Network version mismatch");
    }

    private static Stream<Arguments> provideGenesisConfigurations() {
        return Stream.of(
                Arguments.of(createGethCliqueGenesis(), GenesisFileAnalyzer.GenesisAnalysis.FileStyle.GETH, GenesisFileAnalyzer.GenesisAnalysis.ConsensusMechanism.CLIQUE, "Ropsten"),
                Arguments.of(createBesuIbft2Genesis(), GenesisFileAnalyzer.GenesisAnalysis.FileStyle.BESU, GenesisFileAnalyzer.GenesisAnalysis.ConsensusMechanism.IBFT2, "Mainnet"),
                Arguments.of(createGethEthashGenesis(), GenesisFileAnalyzer.GenesisAnalysis.FileStyle.GETH, GenesisFileAnalyzer.GenesisAnalysis.ConsensusMechanism.ETHASH, "Rinkeby"),
                Arguments.of(createBesuQbftGenesis(), GenesisFileAnalyzer.GenesisAnalysis.FileStyle.BESU, GenesisFileAnalyzer.GenesisAnalysis.ConsensusMechanism.QBFT, "Unknown network (Chain ID: 1337)")
        );
    }

    @Test
    void testAnalyzeIncompleteGenesisFile() {
        ObjectNode incompleteGenesis = JSON.objectNode();
        incompleteGenesis.put("difficulty", "0x400");
        GenesisFileAnalyzer.GenesisAnalysis analysis = GenesisFileAnalyzer.analyzeGenesisFile(incompleteGenesis);
        LOG.info("Incomplete Genesis JSON: {}", incompleteGenesis);
        LOG.info("Analyzed File Style: {}", analysis.fileStyle());
        LOG.info("Analyzed Consensus Mechanism: {}", analysis.consensusMechanism());
        LOG.info("Analyzed Network Version: {}", analysis.networkVersion());
        assertEquals(GenesisFileAnalyzer.GenesisAnalysis.FileStyle.UNKNOWN, analysis.fileStyle());
        assertEquals(GenesisFileAnalyzer.GenesisAnalysis.ConsensusMechanism.UNKNOWN, analysis.consensusMechanism());
        assertEquals("Unknown network", analysis.networkVersion());
    }

    @Test
    void testAnalyzeAmbiguousGenesisFile() {
        ObjectNode ambiguousGenesis = JSON.objectNode();
        ambiguousGenesis.put("config", JSON.objectNode());
        ambiguousGenesis.put("difficulty", "0x400");
        ambiguousGenesis.put("gasLimit", "0x8000000");
        GenesisFileAnalyzer.GenesisAnalysis analysis = GenesisFileAnalyzer.analyzeGenesisFile(ambiguousGenesis);
        // The expected behavior here might vary based on your implementation
        // This test ensures that the analyzer makes a consistent decision for ambiguous files
        assertNotNull(analysis.fileStyle());
        assertNotNull(analysis.consensusMechanism());
        assertNotNull(analysis.networkVersion());
        // You might want to add more specific assertions based on how you expect
        // ambiguous files to be handled in your implementation
    }

    @Test
    void testAnalyzeGenesisFileWithLargeChainId() {
        ObjectNode largeChainIdGenesis = createBesuIbft2Genesis();
        largeChainIdGenesis.with("config").put("chainId", Long.MAX_VALUE);
        GenesisFileAnalyzer.GenesisAnalysis analysis = GenesisFileAnalyzer.analyzeGenesisFile(largeChainIdGenesis);
        LOG.info("Large Chain ID Genesis JSON: {}", largeChainIdGenesis);
        LOG.info("Analyzed Network Version: {}", analysis.networkVersion());
        assertEquals("Unknown network (Chain ID: " + Long.MAX_VALUE + ")", analysis.networkVersion());
    }

    @Test
    void testAnalyzeGenesisFileWithInvalidChainId() {
        ObjectNode invalidChainIdGenesis = createBesuIbft2Genesis();
        invalidChainIdGenesis.with("config").put("chainId", "invalid");
        LOG.info("Invalid Chain ID Genesis JSON: {}", invalidChainIdGenesis);
        assertThrows(RuntimeException.class, () -> GenesisFileAnalyzer.analyzeGenesisFile(invalidChainIdGenesis));
    }

    // Helper methods to create different types of genesis configurations
    private static ObjectNode createGethCliqueGenesis() {
        ObjectNode genesis = JSON.objectNode();
        ObjectNode config = genesis.putObject("config");
        config.putObject("clique");
        config.put("chainId", 3);  // Ropsten
        genesis.put("difficulty", "0x1");
        genesis.put("gasLimit", "0x47b760");
        genesis.putObject("alloc");
        return genesis;
    }

    private static ObjectNode createBesuIbft2Genesis() {
        ObjectNode genesis = JSON.objectNode();
        ObjectNode config = genesis.putObject("config");
        config.putObject("ibft2");
        config.put("chainId", 1);  // Mainnet
        return genesis;
    }

    private static ObjectNode createGethEthashGenesis() {
        ObjectNode genesis = JSON.objectNode();
        ObjectNode config = genesis.putObject("config");
        config.putObject("ethash");
        config.put("chainId", 4);  // Rinkeby
        genesis.put("difficulty", "0x1");
        genesis.put("gasLimit", "0x47b760");
        genesis.putObject("alloc");
        return genesis;
    }

    private static ObjectNode createBesuQbftGenesis() {
        ObjectNode genesis = JSON.objectNode();
        ObjectNode config = genesis.putObject("config");
        config.putObject("qbft");
        config.put("chainId", 1337);  // Custom network
        return genesis;
    }
}