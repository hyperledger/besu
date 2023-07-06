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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods.fork.frontier;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.BlockchainImporter;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcResponseKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcResponseUtils;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcTestMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.testutil.BlockTestUtil;

import java.util.EnumMap;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EthGetUncleByBlockHashAndIndexIntegrationTest {

  private static JsonRpcTestMethodsFactory BLOCKCHAIN;

  private final JsonRpcResponseUtils responseUtils = new JsonRpcResponseUtils();
  private JsonRpcMethod method;

  @BeforeAll
  public static void setUpOnce() throws Exception {
    final String genesisJson =
        Resources.toString(BlockTestUtil.getTestGenesisUrl(), Charsets.UTF_8);

    BLOCKCHAIN =
        new JsonRpcTestMethodsFactory(
            new BlockchainImporter(BlockTestUtil.getTestBlockchainUrl(), genesisJson));
  }

  @BeforeEach
  public void setUp() {
    method = BLOCKCHAIN.methods().get("eth_getUncleByBlockHashAndIndex");
  }

  @Test
  public void shouldGetExpectedBlockResult() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "eth_getUncleByBlockHashAndIndex",
                new Object[] {
                  "0x4e9a67b663f9abe03e7e9fd5452c9497998337077122f44ee78a466f6a7358de", "0x0"
                }));

    final Map<JsonRpcResponseKey, String> out = new EnumMap<>(JsonRpcResponseKey.class);
    out.put(JsonRpcResponseKey.COINBASE, "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");
    out.put(JsonRpcResponseKey.DIFFICULTY, "0x20040");
    out.put(JsonRpcResponseKey.EXTRA_DATA, "0x");
    out.put(JsonRpcResponseKey.GAS_LIMIT, "0x2fefd8");
    out.put(JsonRpcResponseKey.GAS_USED, "0x0");
    out.put(
        JsonRpcResponseKey.LOGS_BLOOM,
        "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    out.put(
        JsonRpcResponseKey.MIX_HASH,
        "0xe970d9815a634e25a778a765764d91ecc80d667a85721dcd4297d00be8d2af29");
    out.put(JsonRpcResponseKey.NONCE, "0x64050e6ee4c2f3c7");
    out.put(JsonRpcResponseKey.NUMBER, "0x2");
    out.put(
        JsonRpcResponseKey.OMMERS_HASH,
        "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347");
    out.put(
        JsonRpcResponseKey.PARENT_HASH,
        "0x10aaf14a53caf27552325374429d3558398a36d3682ede6603c2c6511896e9f9");
    out.put(
        JsonRpcResponseKey.RECEIPTS_ROOT,
        "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
    out.put(
        JsonRpcResponseKey.STATE_ROOT,
        "0xee57559895449b8dbd0a096b2999cf97b517b645ec8db33c7f5934778672263e");
    out.put(JsonRpcResponseKey.SIZE, "0x1ff");
    out.put(JsonRpcResponseKey.TIMESTAMP, "0x561bc2e7");
    out.put(JsonRpcResponseKey.TOTAL_DIFFICULTY, "0x0");
    out.put(
        JsonRpcResponseKey.TRANSACTION_ROOT,
        "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");

    final JsonRpcResponse expected = responseUtils.response(out);
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isInstanceOf(JsonRpcSuccessResponse.class);
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
}
