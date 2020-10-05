package org.hyperledger.besu.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.OptionalLong;

public class EtchashConfigOptions {
    public static final EtchashConfigOptions DEFAULT =
            new EtchashConfigOptions(JsonUtil.createEmptyObjectNode());

    private final ObjectNode etchashConfigRoot;

    EtchashConfigOptions(final ObjectNode etchashConfigRoot) {
        this.etchashConfigRoot = etchashConfigRoot;
    }

    public OptionalLong getEpochLengthActivationBlock() {
        return JsonUtil.getLong(etchashConfigRoot, "epochlengthactivation");
    }

    Map<String, Object> asMap() {
        final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        getEpochLengthActivationBlock().ifPresent(a -> builder.put("epochlengthactivation", a));
        return builder.build();
    }
}
