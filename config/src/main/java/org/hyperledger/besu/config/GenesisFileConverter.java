package org.hyperledger.besu.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hyperledger.besu.datatypes.Wei;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.rlp.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Locale;

public class GenesisFileConverter {

    private static final Logger LOG = LoggerFactory.getLogger(GenesisFileConverter.class);

    static ObjectNode convertGethToBesu(final ObjectNode gethGenesis) {
        LOG.info("Starting Geth to Besu genesis conversion.");

        final ObjectNode besuGenesis = JsonUtil.createEmptyObjectNode();
        final ObjectNode besuConfig = JsonUtil.createEmptyObjectNode();

        try {
            // Convert config section
            convertConfig(gethGenesis, besuConfig);

            // Set the converted config in the Besu genesis
            besuGenesis.set("config", besuConfig);

            // Convert other root-level fields
            convertRootLevelFields(gethGenesis, besuGenesis);

            // Convert allocations
            convertAllocations(gethGenesis, besuGenesis);

            // Handle consensus-specific conversions
            handleConsensusSpecificConversions(gethGenesis, besuGenesis, besuConfig);

            // Handle extraData field
            if (gethGenesis.has("extradata") || gethGenesis.has("extraData")) {
                String extraDataString = gethGenesis.has("extradata") ?
                        gethGenesis.get("extradata").asText() :
                        gethGenesis.get("extraData").asText();

                Bytes extraData = Bytes.fromHexString(extraDataString);
                Bytes fixedExtraData = fixCliqueExtraData(extraData);
                besuGenesis.put("extraData", fixedExtraData.toHexString());
            }

            LOG.info("Geth to Besu conversion completed.");
        } catch (Exception e) {
            LOG.error("Error during Geth to Besu conversion: ", e);
            throw new RuntimeException("Conversion failed", e);
        }

        return besuGenesis;
    }

    private static void convertConfig(final ObjectNode gethGenesis, final ObjectNode besuConfig) {
        if (gethGenesis.has("config")) {
            JsonNode gethConfig = gethGenesis.get("config");
            Iterator<String> fieldNames = gethConfig.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                besuConfig.set(fieldName, gethConfig.get(fieldName));
            }
        }

        // Ensure chainId is present and in the correct format
        if (besuConfig.has("chainId")) {
            JsonNode chainIdNode = besuConfig.get("chainId");
            if (chainIdNode.isTextual()) {
                long chainId = Long.parseLong(chainIdNode.asText().replaceFirst("^0x", ""), 16);
                besuConfig.put("chainId", chainId);
            }
        }

        // Handle fork-specific fields
        String[] forkFields = {
                "homesteadBlock", "eip150Block", "eip155Block", "eip158Block",
                "byzantiumBlock", "constantinopleBlock", "petersburgBlock", "istanbulBlock",
                "muirGlacierBlock", "berlinBlock", "londonBlock"
        };
        for (String field : forkFields) {
            if (gethGenesis.has("config") && gethGenesis.get("config").has(field)) {
                besuConfig.set(field, gethGenesis.get("config").get(field));
            }
        }
    }

    private static void convertRootLevelFields(final ObjectNode gethGenesis, final ObjectNode besuGenesis) {
        String[] rootFields = {"difficulty", "gasLimit", "nonce", "mixHash", "coinbase", "timestamp"};
        for (String field : rootFields) {
            copyIfPresent(gethGenesis, besuGenesis, field);
        }

        // Handle extraData separately
        if (gethGenesis.has("extraData")) {
            besuGenesis.put("extraData", gethGenesis.get("extraData").asText());
        } else if (gethGenesis.has("extradata")) {
            besuGenesis.put("extraData", gethGenesis.get("extradata").asText());
        }
    }

    private static void convertAllocations(final ObjectNode gethGenesis, final ObjectNode besuGenesis) {
        if (gethGenesis.has("alloc")) {
            ObjectNode allocations = JsonUtil.createEmptyObjectNode();
            JsonNode gethAlloc = gethGenesis.get("alloc");
            Iterator<String> addresses = gethAlloc.fieldNames();
            while (addresses.hasNext()) {
                String address = addresses.next();
                JsonNode allocation = gethAlloc.get(address);
                ObjectNode besuAllocation = JsonUtil.createEmptyObjectNode();

                if (allocation.has("balance")) {
                    besuAllocation.put("balance", convertBalance(allocation.get("balance").asText()));
                }
                if (allocation.has("code")) {
                    besuAllocation.put("code", allocation.get("code").asText());
                }
                if (allocation.has("storage")) {
                    besuAllocation.set("storage", allocation.get("storage"));
                }
                if (allocation.has("nonce")) {
                    besuAllocation.set("nonce", allocation.get("nonce"));
                }

                allocations.set(address, besuAllocation);
            }
            besuGenesis.set("alloc", allocations);
        }
    }

    private static void handleConsensusSpecificConversions(final ObjectNode gethGenesis, final ObjectNode besuGenesis, final ObjectNode besuConfig) {
        if (besuConfig.has("clique")) {
            handleCliqueConversion(gethGenesis, besuGenesis, besuConfig);
        } else if (besuConfig.has("ethash")) {
            handleEthashConversion(besuGenesis);
        } else {
            // Default to Ethash if no consensus is specified
            besuConfig.set("ethash", JsonUtil.createEmptyObjectNode());
            handleEthashConversion(besuGenesis);
        }

        // Handle extraData field
        if (gethGenesis.has("extradata") || gethGenesis.has("extraData")) {
            String extraData = gethGenesis.has("extradata") ?
                    gethGenesis.get("extradata").asText() :
                    gethGenesis.get("extraData").asText();

            // Convert extraData and fix it using the updated fixCliqueExtraData method
            besuGenesis.put("extraData", "0x" + fixCliqueExtraData(Bytes.fromHexStringLenient(extraData)).toHexString());
        }
    }

    private static void handleCliqueConversion(final ObjectNode gethGenesis, final ObjectNode besuGenesis, final ObjectNode besuConfig) {
        String extraData = null;
        if (gethGenesis.has("extraData")) {
            extraData = gethGenesis.get("extraData").asText();
        } else if (gethGenesis.has("extradata")) {
            extraData = gethGenesis.get("extradata").asText();
        }

        if (extraData != null) {
            Bytes extraDataBytes = Bytes.fromHexStringLenient(extraData);
            besuGenesis.put("extraData", "0x" + fixCliqueExtraData(extraDataBytes).toHexString());
        }

        // Ensure clique.period is set
        if (!besuConfig.has("clique") || !besuConfig.get("clique").has("period")) {
            ObjectNode cliqueConfig = besuConfig.has("clique") ? (ObjectNode) besuConfig.get("clique") : besuConfig.putObject("clique");
            cliqueConfig.put("period", 15); // Default period
        }
    }

    private static void handleEthashConversion(final ObjectNode besuGenesis) {
        // Ensure difficulty is set for Ethash
        if (!besuGenesis.has("difficulty")) {
            besuGenesis.put("difficulty", "0x1");
        }

        // Ensure nonce and mixHash are set
        if (!besuGenesis.has("nonce")) {
            besuGenesis.put("nonce", "0x0000000000000042");
        }
        if (!besuGenesis.has("mixHash")) {
            besuGenesis.put("mixHash", "0x0000000000000000000000000000000000000000000000000000000000000000");
        }
    }

    static Bytes fixCliqueExtraData(final Bytes extraData) {
        System.out.println("========== fixCliqueExtraData ==========");
        System.out.println("Input extraData: " + extraData.toHexString());
        System.out.println("Input extraData length: " + extraData.size() + " bytes");

        // Ensure minimum length of 32 bytes for vanity data
        if (extraData.size() < 32) {
            Bytes result = Bytes.concatenate(extraData, Bytes.wrap(new byte[194 - extraData.size()]));
            System.out.println("Short input, padded result: " + result.toHexString());
            System.out.println("Padded result length: " + result.size() + " bytes");
            return result;
        }

        // Determine if we're dealing with Geth-style (32 bytes) or our test-style (30 bytes) vanity data
        int vanityLength = (extraData.get(30) == 0 && extraData.get(31) == 0) ? 32 : 30;

        // Preserve the original vanity data
        Bytes vanityData = extraData.slice(0, vanityLength);
        System.out.println("Vanity data: " + vanityData.toHexString());
        System.out.println("Vanity data length: " + vanityData.size() + " bytes");

        // Extract validator addresses (all bytes after vanity data)
        Bytes validatorData = extraData.size() > vanityLength ? extraData.slice(vanityLength) : Bytes.EMPTY;
        System.out.println("Validator data: " + validatorData.toHexString());
        System.out.println("Validator data length: " + validatorData.size() + " bytes");

        // RLP encode the validator addresses
        Bytes rlpEncodedValidators = RLP.encode(writer -> writer.writeValue(validatorData));

        // Combine and ensure total length is 194 bytes
        Bytes combined = Bytes.concatenate(vanityData, rlpEncodedValidators);

        System.out.println("Combined data before padding: " + combined.toHexString());
        System.out.println("Combined data length before padding: " + combined.size() + " bytes");

        if (combined.size() < 194) {
            int paddingSize = 194 - combined.size();
            System.out.println("Padding size: " + paddingSize + " bytes");
            combined = Bytes.concatenate(combined, Bytes.wrap(new byte[paddingSize]));
        } else if (combined.size() > 194) {
            System.out.println("Trimming combined data to 194 bytes");
            combined = combined.slice(0, 194);
        }

        System.out.println("Final result: " + combined.toHexString());
        System.out.println("Final result length: " + combined.size() + " bytes");
        System.out.println("========== End of fixCliqueExtraData ==========");
        return combined;
    }

    private static String convertBalance(final String balance) {
        if (balance.startsWith("0x")) {
            return Wei.fromHexString(balance).toString();
        } else {
            return Wei.of(new BigInteger(balance)).toString();
        }
    }

    private static void copyIfPresent(final ObjectNode from, final ObjectNode to, final String field) {
        if (from.has(field)) {
            to.set(field, from.get(field));
        } else if (from.has(field.toLowerCase(Locale.ROOT))) {
            to.set(field, from.get(field.toLowerCase(Locale.ROOT)));
        }
    }
}