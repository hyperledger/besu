/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.COINBASE;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.DIFFICULTY;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.EXTRA_DATA;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.GAS_LIMIT;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.GAS_USED;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.LOGS_BLOOM;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.MIX_HASH;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.NONCE;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.NUMBER;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.OMMERS_HASH;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.PARENT_HASH;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.RECEIPTS_ROOT;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.SIZE;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.STATE_ROOT;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.TIMESTAMP;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.TOTAL_DIFFICULTY;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey.TRANSACTION_ROOT;

import tech.pegasys.pantheon.ethereum.jsonrpc.BlockchainImporter;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseKey;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcResponseUtils;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcTestMethodsFactory;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.testutil.BlockTestUtil;

import java.util.EnumMap;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class EthGetUncleByBlockHashAndIndexIntegrationTest {

  private static JsonRpcTestMethodsFactory BLOCKCHAIN;

  private final JsonRpcResponseUtils responseUtils = new JsonRpcResponseUtils();
  private JsonRpcMethod method;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    final String genesisJson =
        Resources.toString(BlockTestUtil.getTestGenesisUrl(), Charsets.UTF_8);

    BLOCKCHAIN =
        new JsonRpcTestMethodsFactory(
            new BlockchainImporter(BlockTestUtil.getTestBlockchainUrl(), genesisJson));
  }

  @Before
  public void setUp() {
    method = BLOCKCHAIN.methods().get("eth_getUncleByBlockHashAndIndex");
  }

  @Test
  public void shouldGetExpectedBlockResult() {
    final JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0",
            "eth_getUncleByBlockHashAndIndex",
            new Object[] {
              "0x4e9a67b663f9abe03e7e9fd5452c9497998337077122f44ee78a466f6a7358de", "0x0"
            });

    final Map<JsonRpcResponseKey, String> out = new EnumMap<>(JsonRpcResponseKey.class);
    out.put(COINBASE, "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");
    out.put(DIFFICULTY, "0x20040");
    out.put(EXTRA_DATA, "0x");
    out.put(GAS_LIMIT, "0x2fefd8");
    out.put(GAS_USED, "0x0");
    out.put(
        LOGS_BLOOM,
        "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    out.put(MIX_HASH, "0xe970d9815a634e25a778a765764d91ecc80d667a85721dcd4297d00be8d2af29");
    out.put(NONCE, "0x64050e6ee4c2f3c7");
    out.put(NUMBER, "0x2");
    out.put(OMMERS_HASH, "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347");
    out.put(PARENT_HASH, "0x10aaf14a53caf27552325374429d3558398a36d3682ede6603c2c6511896e9f9");
    out.put(RECEIPTS_ROOT, "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
    out.put(STATE_ROOT, "0xee57559895449b8dbd0a096b2999cf97b517b645ec8db33c7f5934778672263e");
    out.put(SIZE, "0x1ff");
    out.put(TIMESTAMP, "0x561bc2e7");
    out.put(TOTAL_DIFFICULTY, "0x0");
    out.put(TRANSACTION_ROOT, "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");

    final JsonRpcResponse expected = responseUtils.response(out);
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isInstanceOf(JsonRpcSuccessResponse.class);
    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }
}
