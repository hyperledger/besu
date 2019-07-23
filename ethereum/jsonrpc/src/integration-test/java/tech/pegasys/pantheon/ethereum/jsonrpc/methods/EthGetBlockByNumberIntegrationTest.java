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
import static org.assertj.core.api.Assertions.catchThrowable;
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
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.TransactionResult;
import tech.pegasys.pantheon.testutil.BlockTestUtil;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetBlockByNumberIntegrationTest {

  private static final String ETH_METHOD = "eth_getBlockByNumber";
  private static final String JSON_RPC_VERSION = "2.0";
  private static JsonRpcTestMethodsFactory BLOCKCHAIN;

  private final JsonRpcResponseUtils responseUtils = new JsonRpcResponseUtils();
  private Map<String, JsonRpcMethod> methods;

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
    methods = BLOCKCHAIN.methods();
  }

  @Test
  public void ethMethodName() {
    final String ethName = ethGetBlockNumber().getName();

    assertThat(ethName).matches(ETH_METHOD);
  }

  @Test
  public void earliestBlockHashes() {
    final Map<JsonRpcResponseKey, String> out = new EnumMap<>(JsonRpcResponseKey.class);
    out.put(COINBASE, "0x8888f1f195afa192cfee860698584c030f4c9db1");
    out.put(DIFFICULTY, "0x20000");
    out.put(EXTRA_DATA, "0x42");
    out.put(GAS_LIMIT, "0x2fefd8");
    out.put(GAS_USED, "0x0");
    out.put(
        LOGS_BLOOM,
        "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    out.put(MIX_HASH, "0x2c85bcbce56429100b2108254bb56906257582aeafcbd682bc9af67a9f5aee46");
    out.put(NONCE, "0x78cc16f7b4f65485");
    out.put(NUMBER, "0x0");
    out.put(OMMERS_HASH, "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347");
    out.put(PARENT_HASH, "0x0000000000000000000000000000000000000000000000000000000000000000");
    out.put(RECEIPTS_ROOT, "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
    out.put(STATE_ROOT, "0x7dba07d6b448a186e9612e5f737d1c909dce473e53199901a302c00646d523c1");
    out.put(SIZE, "0x1ff");
    out.put(TIMESTAMP, "0x54c98c81");
    out.put(TOTAL_DIFFICULTY, "0x20000");
    out.put(TRANSACTION_ROOT, "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
    final JsonRpcResponse expected = responseUtils.response(out);
    final JsonRpcRequest request = requestWithParams("earliest", false);

    final JsonRpcResponse actual = ethGetBlockNumber().response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void earliestBlockTransactions() {
    final Map<JsonRpcResponseKey, String> out = new EnumMap<>(JsonRpcResponseKey.class);
    out.put(COINBASE, "0x8888f1f195afa192cfee860698584c030f4c9db1");
    out.put(DIFFICULTY, "0x20000");
    out.put(EXTRA_DATA, "0x42");
    out.put(GAS_LIMIT, "0x2fefd8");
    out.put(GAS_USED, "0x0");
    out.put(
        LOGS_BLOOM,
        "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    out.put(MIX_HASH, "0x2c85bcbce56429100b2108254bb56906257582aeafcbd682bc9af67a9f5aee46");
    out.put(NONCE, "0x78cc16f7b4f65485");
    out.put(NUMBER, "0x0");
    out.put(OMMERS_HASH, "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347");
    out.put(PARENT_HASH, "0x0000000000000000000000000000000000000000000000000000000000000000");
    out.put(RECEIPTS_ROOT, "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
    out.put(STATE_ROOT, "0x7dba07d6b448a186e9612e5f737d1c909dce473e53199901a302c00646d523c1");
    out.put(SIZE, "0x1ff");
    out.put(TIMESTAMP, "0x54c98c81");
    out.put(TOTAL_DIFFICULTY, "0x20000");
    out.put(TRANSACTION_ROOT, "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
    final JsonRpcResponse expected = responseUtils.response(out);
    final JsonRpcRequest request = requestWithParams("earliest", true);

    final JsonRpcResponse actual = ethGetBlockNumber().response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void latestBlockHashes() {
    final Map<JsonRpcResponseKey, String> out = new EnumMap<>(JsonRpcResponseKey.class);
    out.put(COINBASE, "0x8888f1f195afa192cfee860698584c030f4c9db1");
    out.put(DIFFICULTY, "0x207c0");
    out.put(EXTRA_DATA, "0x");
    out.put(GAS_LIMIT, "0x2fefd8");
    out.put(GAS_USED, "0x5c99");
    out.put(
        LOGS_BLOOM,
        "0x00000000000000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000040000000000000080000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000400000000000000000200000000000000000002000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000800000000040000000000000000000000000000000000000000010000000000000000000000000");
    out.put(MIX_HASH, "0x4edd77bfff565659bb0ae09421918e4def65d938a900eb94230eb01f5ce80c99");
    out.put(NONCE, "0xdb063000b00e8026");
    out.put(NUMBER, "0x20");
    out.put(OMMERS_HASH, "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347");
    out.put(PARENT_HASH, "0x0f765087745aa259d9e5ac39c367c57432a16ed98e3b0d81c5b51d10f301dc49");
    out.put(RECEIPTS_ROOT, "0xa50a7e67e833f4502524371ee462ccbcc6c6cabd2aeb1555c56150007a53183c");
    out.put(STATE_ROOT, "0xf65f3dd13f72f5fa5607a5224691419969b4f4bae7a00a6cdb853f2ca9eeb1be");
    out.put(SIZE, "0x268");
    out.put(TIMESTAMP, "0x561bc33d");
    out.put(TOTAL_DIFFICULTY, "0x427c00");
    out.put(TRANSACTION_ROOT, "0x6075dd391cf791c74f9e01855d9e5061d009c0903dc102e8b00bcafde8f92839");
    final List<TransactionResult> transactions =
        responseUtils.transactions(
            "0xcef53f2311d7c80e9086d661e69ac11a5f3d081e28e02a9ba9b66749407ac310");
    final JsonRpcResponse expected = responseUtils.response(out, transactions);
    final JsonRpcRequest request = requestWithParams("latest", false);

    final JsonRpcResponse actual = ethGetBlockNumber().response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void latestBlockTransactions() {
    final Map<JsonRpcResponseKey, String> out = new EnumMap<>(JsonRpcResponseKey.class);
    out.put(COINBASE, "0x8888f1f195afa192cfee860698584c030f4c9db1");
    out.put(DIFFICULTY, "0x207c0");
    out.put(EXTRA_DATA, "0x");
    out.put(GAS_LIMIT, "0x2fefd8");
    out.put(GAS_USED, "0x5c99");
    out.put(
        LOGS_BLOOM,
        "0x00000000000000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000040000000000000080000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000400000000000000000200000000000000000002000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000800000000040000000000000000000000000000000000000000010000000000000000000000000");
    out.put(MIX_HASH, "0x4edd77bfff565659bb0ae09421918e4def65d938a900eb94230eb01f5ce80c99");
    out.put(NONCE, "0xdb063000b00e8026");
    out.put(NUMBER, "0x20");
    out.put(OMMERS_HASH, "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347");
    out.put(PARENT_HASH, "0x0f765087745aa259d9e5ac39c367c57432a16ed98e3b0d81c5b51d10f301dc49");
    out.put(RECEIPTS_ROOT, "0xa50a7e67e833f4502524371ee462ccbcc6c6cabd2aeb1555c56150007a53183c");
    out.put(STATE_ROOT, "0xf65f3dd13f72f5fa5607a5224691419969b4f4bae7a00a6cdb853f2ca9eeb1be");
    out.put(SIZE, "0x268");
    out.put(TIMESTAMP, "0x561bc33d");
    out.put(TOTAL_DIFFICULTY, "0x427c00");
    out.put(TRANSACTION_ROOT, "0x6075dd391cf791c74f9e01855d9e5061d009c0903dc102e8b00bcafde8f92839");
    final List<TransactionResult> transactions =
        responseUtils.transactions(
            responseUtils.transaction(
                "0x71d59849ddd98543bdfbe8548f5eed559b07b8aaf196369f39134500eab68e53",
                "0x20",
                "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b",
                "0x4cb2f",
                "0x1",
                "0xcef53f2311d7c80e9086d661e69ac11a5f3d081e28e02a9ba9b66749407ac310",
                "0x9dc2c8f5",
                "0x1f",
                "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f",
                "0x0",
                "0xa",
                "0x1b",
                "0x705b002a7df60707d33812e0298411721be20ea5a2f533707295140d89263b79",
                "0x78024390784f24160739533b3ceea2698289a02afd9cc768581b4aa3d5f4b105"));
    final JsonRpcResponse expected = responseUtils.response(out, transactions);
    final JsonRpcRequest request = requestWithParams("latest", true);

    final JsonRpcResponse actual = ethGetBlockNumber().response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void pendingBlockHashes() {
    final JsonRpcResponse expected = new JsonRpcSuccessResponse(null, null);
    final JsonRpcRequest request = requestWithParams("pending", false);

    final JsonRpcResponse actual = ethGetBlockNumber().response(request);

    assertThat(actual).isEqualToComparingFieldByField(expected);
  }

  @Test
  public void pendingBlockTransactions() {
    final JsonRpcResponse expected = new JsonRpcSuccessResponse(null, null);
    final JsonRpcRequest request = requestWithParams("pending", true);

    final JsonRpcResponse actual = ethGetBlockNumber().response(request);

    assertThat(actual).isEqualToComparingFieldByField(expected);
  }

  @Test
  public void blockSixHashes() {
    final Map<JsonRpcResponseKey, String> out = new EnumMap<>(JsonRpcResponseKey.class);
    out.put(COINBASE, "0x8888f1f195afa192cfee860698584c030f4c9db1");
    out.put(DIFFICULTY, "0x20100");
    out.put(EXTRA_DATA, "0x");
    out.put(GAS_LIMIT, "0x2fefd8");
    out.put(GAS_USED, "0x559f");
    out.put(
        LOGS_BLOOM,
        "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    out.put(MIX_HASH, "0x1657e6f42fc186c23d921ba9bcf93f287db353762682f675fa3969757e410e00");
    out.put(NONCE, "0xb65c663250417c60");
    out.put(NUMBER, "0x5");
    out.put(OMMERS_HASH, "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347");
    out.put(PARENT_HASH, "0x4e9a67b663f9abe03e7e9fd5452c9497998337077122f44ee78a466f6a7358de");
    out.put(RECEIPTS_ROOT, "0x01bf16fce84572feb648e5f3487eb3b6648a49639888a90eb552aa661f38f8bd");
    out.put(STATE_ROOT, "0x0c7a49b1ae3138ae33d88b21d5543b8d2c8e2377bd2b58e73db8ea8924395ff4");
    out.put(SIZE, "0x268");
    out.put(TIMESTAMP, "0x561bc2ec");
    out.put(TOTAL_DIFFICULTY, "0xc0280");
    out.put(TRANSACTION_ROOT, "0xd8672f45d109c2e0b27acf68fd67b9eae14957fd2bf2444210ee0d7e97bc68a6");
    final List<TransactionResult> transactions =
        responseUtils.transactions(
            "0xec7e53d1b99ef586b3e43c1c7068311f6861d51ac3d6fbf257ac0b54ba3f2032");
    final JsonRpcResponse expected = responseUtils.response(out, transactions);
    final JsonRpcRequest request = requestWithParams("0x5", false);

    final JsonRpcResponse actual = ethGetBlockNumber().response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void blockSixTransactions() {
    final Map<JsonRpcResponseKey, String> out = new EnumMap<>(JsonRpcResponseKey.class);
    out.put(COINBASE, "0x8888f1f195afa192cfee860698584c030f4c9db1");
    out.put(DIFFICULTY, "0x20100");
    out.put(EXTRA_DATA, "0x");
    out.put(GAS_LIMIT, "0x2fefd8");
    out.put(GAS_USED, "0x559f");
    out.put(
        LOGS_BLOOM,
        "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    out.put(MIX_HASH, "0x1657e6f42fc186c23d921ba9bcf93f287db353762682f675fa3969757e410e00");
    out.put(NONCE, "0xb65c663250417c60");
    out.put(NUMBER, "0x5");
    out.put(OMMERS_HASH, "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347");
    out.put(PARENT_HASH, "0x4e9a67b663f9abe03e7e9fd5452c9497998337077122f44ee78a466f6a7358de");
    out.put(RECEIPTS_ROOT, "0x01bf16fce84572feb648e5f3487eb3b6648a49639888a90eb552aa661f38f8bd");
    out.put(STATE_ROOT, "0x0c7a49b1ae3138ae33d88b21d5543b8d2c8e2377bd2b58e73db8ea8924395ff4");
    out.put(SIZE, "0x268");
    out.put(TIMESTAMP, "0x561bc2ec");
    out.put(TOTAL_DIFFICULTY, "0xc0280");
    out.put(TRANSACTION_ROOT, "0xd8672f45d109c2e0b27acf68fd67b9eae14957fd2bf2444210ee0d7e97bc68a6");
    final List<TransactionResult> transactions =
        responseUtils.transactions(
            responseUtils.transaction(
                "0x609427ccfeae6d2a930927c9a29a0a3077cac7e4b5826159586b10e25770eef9",
                "0x5",
                "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b",
                "0x4cb2f",
                "0x1",
                "0xec7e53d1b99ef586b3e43c1c7068311f6861d51ac3d6fbf257ac0b54ba3f2032",
                "0xf5b53e17",
                "0x4",
                "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f",
                "0x0",
                "0xa",
                "0x1c",
                "0x1c07bd41fc821f95b9f543b080c520654727f9cf829800f789c3b03b8de8b326",
                "0x259c8aceea2d462192d95f9d6b7cb9e0bf2a6d549c3a4111194fdd22105728f5"));
    final JsonRpcResponse expected = responseUtils.response(out, transactions);
    final JsonRpcRequest request = requestWithParams("0x5", true);

    final JsonRpcResponse actual = ethGetBlockNumber().response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  /** The Tag | Quantity is the first parameter, either a String or a number */
  @Test
  public void missingTagParameterBlockHashes() {
    final JsonRpcRequest request = requestWithParams(false);

    final Throwable thrown = catchThrowable(() -> ethGetBlockNumber().response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid json rpc parameter at index 0");
  }

  /** The Tag | Quantity is the first parameter, either a String or a number */
  @Test
  public void missingTagParameterBlockTransactions() {
    final JsonRpcRequest request = requestWithParams(true);

    final Throwable thrown = catchThrowable(() -> ethGetBlockNumber().response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid json rpc parameter at index 0");
  }

  /**
   * The Boolean type second parameter, denotes whether to retrieve the complete transaction or just
   * the transaction hash.
   */
  @Test
  public void missingHashesOrTransactionParameter() {
    final JsonRpcRequest request = requestWithParams("earliest");

    final Throwable thrown = catchThrowable(() -> ethGetBlockNumber().response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasNoCause()
        .hasMessage("Missing required json rpc parameter at index 1");
  }

  /**
   * The Boolean type second parameter, denotes whether to retrieve the complete transaction or just
   * the transaction hash.
   */
  @Test
  public void missingAllParameters() {
    final JsonRpcRequest request = requestWithParams();

    final Throwable thrown = catchThrowable(() -> ethGetBlockNumber().response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasNoCause()
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params);
  }

  private JsonRpcMethod ethGetBlockNumber() {
    final JsonRpcMethod method = methods.get(ETH_METHOD);
    assertThat(method).isNotNull();
    return method;
  }
}
