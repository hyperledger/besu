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
package org.hyperledger.besu.ethereum.retesteth.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.retesteth.RetestethContext;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestSetChainParams implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(TestSetChainParams.class);

  public static final String METHOD_NAME = "test_setChainParams";
  private final RetestethContext context;

  public TestSetChainParams(final RetestethContext context) {
    this.context = context;
  }

  @Override
  public String getName() {
    return METHOD_NAME;
  }

  @SuppressWarnings("unchecked")
  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {

    try {
      final JsonObject chainParamsAsJson =
          new JsonObject((Map<String, Object>) requestContext.getRequest().getParams()[0]);
      final String chainParamsAsString = chainParamsAsJson.encodePrettily();
      LOG.trace("ChainParams {}", chainParamsAsString);
      final String genesisFileAsString = modifyGenesisFile(chainParamsAsString);
      LOG.trace("Genesis {}", genesisFileAsString);
      final boolean result =
          context.resetContext(
              genesisFileAsString,
              chainParamsAsJson.getString("sealEngine", "NoProof"),
              Optional.empty());

      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
    } catch (final Exception e) {
      LOG.error("Unhandled error", e);
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
  }

  private static void maybeMove(
      final JsonObject src, final String srcName, final JsonObject dest, final String destName) {
    if (src.containsKey(srcName)) {
      dest.put(destName, src.getValue(srcName));
      src.remove(srcName);
    }
  }

  private static void maybeMoveToNumber(
      final JsonObject src, final String srcName, final JsonObject dest, final String destName) {
    if (src.containsKey(srcName)) {
      dest.put(destName, Long.decode(src.getString(srcName)));
      src.remove(srcName);
    }
  }

  private static void maybeMoveToNumber(
      final JsonObject src,
      final String srcName,
      final JsonObject dest,
      final String destName,
      final long defaultValue) {
    if (src.containsKey(srcName)) {
      dest.put(destName, Long.decode(src.getString(srcName)));
      src.remove(srcName);
    } else {
      dest.put(destName, defaultValue);
    }
  }

  private static String modifyGenesisFile(final String initialGenesis) {
    final JsonObject chainParamsJson = new JsonObject(initialGenesis);
    final JsonObject config = new JsonObject();
    chainParamsJson.put("config", config);
    final JsonObject params = chainParamsJson.getJsonObject("params");
    final JsonObject genesis = chainParamsJson.getJsonObject("genesis");

    // Whether sealEngine is NoProof, Ethash, or NoReward the genesis file is the same
    final JsonObject ethash = new JsonObject();
    config.put("ethash", ethash);

    maybeMoveToNumber(params, "homesteadForkBlock", config, "homesteadBlock");
    maybeMoveToNumber(params, "daoHardforkBlock", config, "daoForkBlock");
    maybeMoveToNumber(params, "EIP150ForkBlock", config, "eip150Block");
    maybeMoveToNumber(params, "EIP158ForkBlock", config, "eip158Block");
    maybeMoveToNumber(params, "byzantiumForkBlock", config, "byzantiumBlock");
    maybeMoveToNumber(params, "constantinopleForkBlock", config, "constantinopleBlock");
    maybeMoveToNumber(params, "constantinopleFixForkBlock", config, "petersburgBlock");
    maybeMoveToNumber(params, "istanbulForkBlock", config, "istanbulBlock");
    maybeMoveToNumber(params, "muirGlacierForkBlock", config, "muirGlacierBlock");
    maybeMoveToNumber(params, "berlinForkBlock", config, "berlinBlock");
    maybeMoveToNumber(params, "londonForkBlock", config, "londonBlock");
    maybeMoveToNumber(params, "arrowGlacierForkBlock", config, "arrowGlacierBlock");
    maybeMoveToNumber(params, "grayGlacierForkBlock", config, "grayGlacierBlock");
    maybeMoveToNumber(params, "mergeNetSplitForkBlock", config, "mergeNetSplitBlock");
    maybeMoveToNumber(params, "chainID", config, "chainId", 1);

    maybeMove(genesis, "author", chainParamsJson, "coinbase");
    maybeMove(genesis, "difficulty", chainParamsJson, "difficulty");
    maybeMove(genesis, "extraData", chainParamsJson, "extraData");
    maybeMove(genesis, "gasLimit", chainParamsJson, "gasLimit");
    maybeMove(genesis, "mixHash", chainParamsJson, "mixHash");
    maybeMove(genesis, "nonce", chainParamsJson, "nonce");
    maybeMove(genesis, "timestamp", chainParamsJson, "timestamp");
    maybeMove(chainParamsJson, "accounts", chainParamsJson, "alloc");
    maybeMove(genesis, "baseFeePerGas", chainParamsJson, "baseFeePerGas");

    // strip out precompiles with zero balance
    final JsonObject alloc = chainParamsJson.getJsonObject("alloc");
    final Iterator<String> fieldNamesIter = alloc.fieldNames().iterator();
    while (fieldNamesIter.hasNext()) {
      final String address = fieldNamesIter.next();
      final JsonObject account = alloc.getJsonObject(address);
      if (account.containsKey("precompiled") && !account.containsKey("balance")) {
        fieldNamesIter.remove();
      }
    }

    return chainParamsJson.encodePrettily();
  }
}
