package org.hyperledger.besu.ethereum.eth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.json.JsonObject;
import org.hyperledger.besu.config.JsonGenesisConfigOptions;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ForkIdGenesisTest {
    private static final JsonObject GENESIS_FORKS =
            (new JsonObject()).put("config", new JsonObject().put("isquorum", true));
    private static final JsonObject GENESIS_QIP714BLOCK =
            (new JsonObject()).put("config", new JsonObject().put("isquorum", true));

    final BlockDataGenerator blockGenerator = new BlockDataGenerator();
    final Block genesis = blockGenerator.genesisBlock(new BlockDataGenerator.BlockOptions());

    @Test
    public void blah1() {
        final ObjectNode jsonNodes = JsonUtil.objectNodeFromString(GENESIS_FORKS.toString());
        final JsonGenesisConfigOptions jsonGenesisConfigOptions = JsonGenesisConfigOptions.fromJsonObject(jsonNodes);

        new LegacyForkIdManager();

        System.out.println();
    }

    @Test
    public void blah2() {

    }
}
