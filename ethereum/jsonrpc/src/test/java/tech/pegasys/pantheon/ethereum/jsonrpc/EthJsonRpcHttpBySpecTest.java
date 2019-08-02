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
package tech.pegasys.pantheon.ethereum.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.DebugAccountRange;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthBlockNumber;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthCall;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthEstimateGas;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBalance;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBlockByNumber;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBlockTransactionCountByHash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBlockTransactionCountByNumber;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetCode;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetFilterChanges;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetLogs;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetStorageAt;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionByBlockHashAndIndex;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionByBlockNumberAndIndex;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionByHash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionCount;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionReceipt;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthNewBlockFilter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthNewFilter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthNewPendingTransactionFilter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthProtocolVersion;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthSendRawTransaction;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthUninstallFilter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;

import java.io.IOException;
import java.util.Collection;

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EthJsonRpcHttpBySpecTest extends AbstractEthJsonRpcHttpServiceTest {

  private final String specFileName;

  public EthJsonRpcHttpBySpecTest(final String specFileName) {
    this.specFileName = specFileName;
  }

  /*
   Mapping between Json-RPC method class and its spec files

   Formatter will be turned on to make this easier to read (one spec per line)
   @formatter:off
  */
  @Parameters(name = "{index}: {0}")
  public static Collection<String> specs() {
    final Multimap<Class<? extends JsonRpcMethod>, String> specs = ArrayListMultimap.create();

    specs.put(EthGetTransactionByHash.class, "eth_getTransactionByHash_addressReceiver");
    specs.put(EthGetTransactionByHash.class, "eth_getTransactionByHash_contractCreation");
    specs.put(EthGetTransactionByHash.class, "eth_getTransactionByHash_null");
    specs.put(EthGetTransactionByHash.class, "eth_getTransactionByHash_invalidParams");
    specs.put(EthGetTransactionByHash.class, "eth_getTransactionByHash_typeMismatch");
    specs.put(EthGetTransactionByHash.class, "eth_getTransactionByHash_invalidHashAndIndex");

    specs.put(EthGetBalance.class, "eth_getBalance_latest");
    specs.put(EthGetBalance.class, "eth_getBalance_illegalRangeGreaterThan");
    specs.put(EthGetBalance.class, "eth_getBalance_illegalRangeLessThan");
    specs.put(EthGetBalance.class, "eth_getBalance_invalidParams");

    specs.put(EthGetBlockByNumber.class, "eth_getBlockByNumber_complete");
    specs.put(EthGetBlockByNumber.class, "eth_getBlockByNumber_hashes");

    specs.put(EthGetStorageAt.class, "eth_getStorageAt_latest");
    specs.put(EthGetStorageAt.class, "eth_getStorageAt_invalidParams");
    specs.put(EthGetStorageAt.class, "eth_getStorageAt_illegalRangeGreaterThan");
    specs.put(EthGetStorageAt.class, "eth_getStorageAt_illegalRangeLessThan");

    specs.put(EthGetTransactionReceipt.class, "eth_getTransactionReceipt_contractAddress");
    specs.put(EthGetTransactionReceipt.class, "eth_getTransactionReceipt_nullContractAddress");
    specs.put(EthGetTransactionReceipt.class, "eth_getTransactionReceipt_logs");

    specs.put(EthGetLogs.class, "eth_getLogs_invalidInput");
    specs.put(EthGetLogs.class, "eth_getLogs_blockhash");
    specs.put(EthGetLogs.class, "eth_getLogs_blockhash_missingBlockHash");
    specs.put(EthGetLogs.class, "eth_getLogs_toBlockOutOfRange");
    specs.put(EthGetLogs.class, "eth_getLogs_fromBlockExceedToBlock");
    specs.put(EthGetLogs.class, "eth_getLogs_nullParam");
    specs.put(EthGetLogs.class, "eth_getLogs_matchTopic");
    specs.put(EthGetLogs.class, "eth_getLogs_failTopicPosition");

    specs.put(EthNewFilter.class, "eth_getNewFilter_validFilterLatestBlock");
    specs.put(EthNewFilter.class, "eth_getNewFilter_validFilterWithBlockNumber");
    specs.put(EthNewFilter.class, "eth_getNewFilter_invalidFilter");
    specs.put(EthNewFilter.class, "eth_getNewFilter_emptyFilter");
    specs.put(EthNewFilter.class, "eth_getNewFilter_addressOnly");
    specs.put(EthNewFilter.class, "eth_getNewFilter_topicOnly");

    specs.put(
        EthGetTransactionByBlockHashAndIndex.class, "eth_getTransactionByBlockHashAndIndex_null");
    specs.put(
        EthGetTransactionByBlockHashAndIndex.class,
        "eth_getTransactionByBlockHashAndIndex_intOverflow");
    specs.put(
        EthGetTransactionByBlockHashAndIndex.class,
        "eth_getTransactionByBlockHashAndIndex_wrongParamType");
    specs.put(
        EthGetTransactionByBlockHashAndIndex.class,
        "eth_getTransactionByBlockHashAndIndex_missingParams");
    specs.put(
        EthGetTransactionByBlockHashAndIndex.class,
        "eth_getTransactionByBlockHashAndIndex_missingParam_00");
    specs.put(
        EthGetTransactionByBlockHashAndIndex.class,
        "eth_getTransactionByBlockHashAndIndex_missingParam_01");
    specs.put(
        EthGetTransactionByBlockHashAndIndex.class, "eth_getTransactionByBlockHashAndIndex_00");
    specs.put(
        EthGetTransactionByBlockHashAndIndex.class, "eth_getTransactionByBlockHashAndIndex_01");
    specs.put(
        EthGetTransactionByBlockHashAndIndex.class, "eth_getTransactionByBlockHashAndIndex_02");

    specs.put(
        EthGetTransactionByBlockNumberAndIndex.class,
        "eth_getTransactionByBlockNumberAndIndex_null");
    specs.put(
        EthGetTransactionByBlockNumberAndIndex.class,
        "eth_getTransactionByBlockNumberAndIndex_latest");
    specs.put(
        EthGetTransactionByBlockNumberAndIndex.class,
        "eth_getTransactionByBlockNumberAndIndex_earliestNull");
    specs.put(
        EthGetTransactionByBlockNumberAndIndex.class,
        "eth_getTransactionByBlockNumberAndIndex_pendingNull");
    specs.put(
        EthGetTransactionByBlockNumberAndIndex.class,
        "eth_getTransactionByBlockNumberAndIndex_invalidParams");
    specs.put(
        EthGetTransactionByBlockNumberAndIndex.class, "eth_getTransactionByBlockNumberAndIndex_00");
    specs.put(
        EthGetTransactionByBlockNumberAndIndex.class, "eth_getTransactionByBlockNumberAndIndex_01");

    specs.put(
        EthGetBlockTransactionCountByNumber.class,
        "eth_getBlockTransactionCountByNumber_invalidParams");
    specs.put(
        EthGetBlockTransactionCountByNumber.class, "eth_getBlockTransactionCountByNumber_null");
    specs.put(
        EthGetBlockTransactionCountByNumber.class, "eth_getBlockTransactionCountByNumber_earliest");
    specs.put(
        EthGetBlockTransactionCountByNumber.class, "eth_getBlockTransactionCountByNumber_latest");
    specs.put(EthGetBlockTransactionCountByNumber.class, "eth_getBlockTransactionCountByNumber_00");
    specs.put(
        EthGetBlockTransactionCountByNumber.class,
        "eth_getBlockTransactionCountByNumber_illegalRangeGreaterThan");
    specs.put(
        EthGetBlockTransactionCountByNumber.class,
        "eth_getBlockTransactionCountByNumber_illegalRangeLessThan");

    specs.put(
        EthGetBlockTransactionCountByHash.class,
        "eth_getBlockTransactionCountByHash_invalidParams");
    specs.put(
        EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_noResult");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_00");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_01");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_02");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_03");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_04");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_05");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_06");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_07");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_08");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_09");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_10");
    specs.put(EthGetBlockTransactionCountByHash.class, "eth_getBlockTransactionCountByHash_11");

    specs.put(EthGetTransactionCount.class, "eth_getTransactionCount_illegalRange");
    specs.put(EthGetTransactionCount.class, "eth_getTransactionCount_latest");
    specs.put(EthGetTransactionCount.class, "eth_getTransactionCount_earliest");
    specs.put(EthGetTransactionCount.class, "eth_getTransactionCount_blockNumber");
    specs.put(EthGetTransactionCount.class, "eth_getTransactionCount_missingArgument");

    specs.put(EthGetCode.class, "eth_getCode_illegalRangeLessThan");
    specs.put(EthGetCode.class, "eth_getCode_illegalRangeGreaterThan");
    specs.put(EthGetCode.class, "eth_getCode_success");
    specs.put(EthGetCode.class, "eth_getCode_noCodeNumber");
    specs.put(EthGetCode.class, "eth_getCode_noCodeLatest");
    specs.put(EthGetCode.class, "eth_getCode_invalidParams");

    specs.put(EthBlockNumber.class, "eth_blockNumber");

    specs.put(EthCall.class, "eth_call_earliestBlock");
    specs.put(EthCall.class, "eth_call_block_8");
    specs.put(EthCall.class, "eth_call_gasLimitTooLow_block_8");
    specs.put(EthCall.class, "eth_call_gasPriceTooHigh_block_8");
    specs.put(EthCall.class, "eth_call_valueTooHigh_block_8");
    specs.put(EthCall.class, "eth_call_callParamsMissing_block_8");
    specs.put(EthCall.class, "eth_call_toMissing_block_8");
    specs.put(EthCall.class, "eth_call_latestBlock");

    specs.put(EthNewBlockFilter.class, "eth_newBlockFilter");

    specs.put(EthNewPendingTransactionFilter.class, "eth_newPendingTransactionFilter");

    specs.put(EthUninstallFilter.class, "eth_uninstallFilter_NonexistentFilter");
    specs.put(EthUninstallFilter.class, "eth_uninstallFilter_FilterIdTooLong");
    specs.put(EthUninstallFilter.class, "eth_uninstallFilter_FilterIdNegative");

    specs.put(EthGetFilterChanges.class, "eth_getFilterChanges_NonexistentFilter");
    specs.put(EthGetFilterChanges.class, "eth_getFilterChanges_FilterIdTooLong");
    specs.put(EthGetFilterChanges.class, "eth_getFilterChanges_FilterIdNegative");

    specs.put(EthSendRawTransaction.class, "eth_sendRawTransaction_transferEther");
    specs.put(EthSendRawTransaction.class, "eth_sendRawTransaction_contractCreation");
    specs.put(EthSendRawTransaction.class, "eth_sendRawTransaction_messageCall");
    specs.put(EthSendRawTransaction.class, "eth_sendRawTransaction_invalidByteValueHex");
    specs.put(EthSendRawTransaction.class, "eth_sendRawTransaction_invalidNonceTooLow");
    specs.put(EthSendRawTransaction.class, "eth_sendRawTransaction_invalidRawTransaction");
    specs.put(EthSendRawTransaction.class, "eth_sendRawTransaction_unsignedTransaction");

    specs.put(EthEstimateGas.class, "eth_estimateGas_contractDeploy");
    specs.put(EthEstimateGas.class, "eth_estimateGas_transfer");
    specs.put(EthEstimateGas.class, "eth_estimateGas_noParams");
    specs.put(EthEstimateGas.class, "eth_estimateGas_insufficientGas");

    specs.put(EthProtocolVersion.class, "eth_protocolVersion");

    specs.put(DebugAccountRange.class, "debug_accountRange_blockHash");
    specs.put(DebugAccountRange.class, "debug_accountRange_complete");
    specs.put(DebugAccountRange.class, "debug_accountRange_partial");
    specs.put(DebugAccountRange.class, "debug_storageRangeAt_blockHash");
    specs.put(DebugAccountRange.class, "debug_storageRangeAt_blockNumber");
    specs.put(DebugAccountRange.class, "debug_storageRangeAt_midBlock");

    return specs.values();
  }

  @Test
  public void jsonRPCCallWithSpecFile() throws Exception {
    jsonRPCCall(specFileName);
  }

  private void jsonRPCCall(final String name) throws IOException {
    final String testSpecFile = name + ".json";
    final String json =
        Resources.toString(
            EthJsonRpcHttpBySpecTest.class.getResource(testSpecFile), Charsets.UTF_8);
    final JsonObject spec = new JsonObject(json);

    final String rawRequestBody = spec.getJsonObject("request").toString();
    final RequestBody requestBody = RequestBody.create(JSON, rawRequestBody);
    final Request request = new Request.Builder().post(requestBody).url(baseUrl).build();

    importBlocks(1, BLOCKS.size());
    try (final Response resp = client.newCall(request).execute()) {
      final int expectedStatusCode = spec.getInteger("statusCode");
      assertThat(resp.code()).isEqualTo(expectedStatusCode);

      final String expectedRespBody = spec.getJsonObject("response").encodePrettily();
      assertThat(resp.body().string()).isEqualTo(expectedRespBody);
    }
  }

  private void importBlocks(final int from, final int to) {
    for (int i = from; i < to; ++i) {
      importBlock(i);
    }
  }
}
