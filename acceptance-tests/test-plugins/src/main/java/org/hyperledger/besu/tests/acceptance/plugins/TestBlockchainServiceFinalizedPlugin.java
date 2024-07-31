/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.tests.acceptance.plugins;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.hyperledger.besu.plugin.services.exception.PluginRpcEndpointException;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;

import java.util.Optional;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(BesuPlugin.class)
public class TestBlockchainServiceFinalizedPlugin implements BesuPlugin {
  private static final Logger LOG =
      LoggerFactory.getLogger(TestBlockchainServiceFinalizedPlugin.class);
  private static final String RPC_NAMESPACE = "updater";
  private static final String RPC_METHOD_NAME = "updateFinalizedBlockV1";

  @Override
  public void register(final BesuContext besuContext) {
    LOG.trace("Registering plugin ...");

    final RpcEndpointService rpcEndpointService =
        besuContext
            .getService(RpcEndpointService.class)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Failed to obtain RpcEndpointService from the BesuContext."));

    final BlockchainService blockchainService =
        besuContext
            .getService(BlockchainService.class)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Failed to obtain BlockchainService from the BesuContext."));

    final FinalizationUpdaterRpcMethod rpcMethod =
        new FinalizationUpdaterRpcMethod(blockchainService);
    rpcEndpointService.registerRPCEndpoint(RPC_NAMESPACE, RPC_METHOD_NAME, rpcMethod::execute);
  }

  @Override
  public void start() {
    LOG.trace("Starting plugin ...");
  }

  @Override
  public void stop() {
    LOG.trace("Stopping plugin ...");
  }

  static class FinalizationUpdaterRpcMethod {
    private final BlockchainService blockchainService;
    private final JsonRpcParameter parameterParser = new JsonRpcParameter();

    FinalizationUpdaterRpcMethod(final BlockchainService blockchainService) {
      this.blockchainService = blockchainService;
    }

    Boolean execute(final PluginRpcRequest request) {
      final Long finalizedBlockNumber = parseResult(request);

      // lookup finalized block by number in local chain
      final Optional<BlockContext> finalizedBlock =
          blockchainService.getBlockByNumber(finalizedBlockNumber);
      if (finalizedBlock.isEmpty()) {
        throw new PluginRpcEndpointException(
            RpcErrorType.BLOCK_NOT_FOUND,
            "Block not found in the local chain: " + finalizedBlockNumber);
      }

      try {
        blockchainService.setFinalizedBlock(finalizedBlock.get().getBlockHeader().getBlockHash());
      } catch (final IllegalArgumentException e) {
        throw new PluginRpcEndpointException(
            RpcErrorType.BLOCK_NOT_FOUND,
            "Block not found in the local chain: " + finalizedBlockNumber);
      } catch (final UnsupportedOperationException e) {
        throw new PluginRpcEndpointException(
            RpcErrorType.METHOD_NOT_ENABLED,
            "Method not enabled for PoS network: setFinalizedBlock");
      } catch (final Exception e) {
        throw new PluginRpcEndpointException(
            RpcErrorType.INTERNAL_ERROR, "Error setting finalized block: " + finalizedBlockNumber);
      }

      return Boolean.TRUE;
    }

    private Long parseResult(final PluginRpcRequest request) {
      Long blockNumber;
      try {
        final Object[] params = request.getParams();
        blockNumber = parameterParser.required(params, 0, Long.class);
      } catch (final Exception e) {
        throw new PluginRpcEndpointException(RpcErrorType.INVALID_PARAMS, e.getMessage());
      }

      if (blockNumber <= 0) {
        throw new PluginRpcEndpointException(
            RpcErrorType.INVALID_PARAMS, "Block number must be greater than 0");
      }

      return blockNumber;
    }
  }
}
