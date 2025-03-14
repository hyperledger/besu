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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestProcessorCoordinator;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.zip.CRC32;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Suppliers;

public class EthConfig implements JsonRpcMethod {

  private static final Supplier<ObjectMapper> mapperSupplier = Suppliers.memoize(ObjectMapper::new);

  private final BlockchainQueries blockchain;
  private final ProtocolSchedule protocolSchedule;

  public EthConfig(final BlockchainQueries blockchain, final ProtocolSchedule protocolSchedule) {
    this.blockchain = blockchain;
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_CONFIG.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    var header = blockchain.getBlockchain().getChainHeadHeader();
    var current = protocolSchedule.getForNextBlockHeader(header, System.currentTimeMillis() / 1000);
    var next = protocolSchedule.getNextProtocolSpec(current);

    var currentConfig = generateConfig(current);
    var nextConfig = next.map(this::generateConfig).orElse(null);

    ObjectNode result = mapperSupplier.get().createObjectNode();
    result.put("current", currentConfig);
    result.put("currentHash", configHash(currentConfig));
    result.put("next", nextConfig);
    result.put("nextHash", configHash(nextConfig));

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
  }

  JsonNode generateConfig(final ProtocolSpec spec) {
    var result = mapperSupplier.get().createObjectNode();
    var forkId = protocolSchedule.hardforkFor(x -> x.spec() == spec).orElseThrow();

    result.put("activation", forkId.milestone());

    var blobs = result.putObject("blobs");
    blobs.put(
        "baseFeeUpdateFraction", spec.getFeeMarket().getBaseFeeUpdateFraction().longValueExact());
    blobs.put("max", spec.getGasLimitCalculator().currentBlobGasLimit() / (128 * 1024));
    blobs.put("target", spec.getGasCalculator().getBlobTarget());

    result.put(
        "chainId", protocolSchedule.getChainId().map(c -> "0x" + c.toString(16)).orElse(null));

    var contracts =
        new TreeMap<>(
            spec.getRequestProcessorCoordinator()
                .map(RequestProcessorCoordinator::getContractConfigs)
                .orElse(Map.of()));
    spec.getBlockHashProcessor()
        .getHistoryContract()
        .ifPresent(a -> contracts.put("HISTORY", a.toHexString()));
    if (!contracts.isEmpty()) {
      var jsonContracts = result.putObject("contracts");
      contracts.forEach(jsonContracts::put);
    }

    PrecompileContractRegistry registry = spec.getPrecompileContractRegistry();
    var precompiles = result.putObject("precompiles");
    registry.getPrecompileAddresses().stream()
        .sorted()
        .forEach(a -> precompiles.put(a.toHexString(), registry.get(a).getName()));

    return result;
  }

  String configHash(final JsonNode node) {
    if (node == null) {
      return null;
    } else {
      final CRC32 crc = new CRC32();
      crc.update(node.toString().getBytes(StandardCharsets.UTF_8));
      return Long.toHexString(crc.getValue());
    }
  }
}
