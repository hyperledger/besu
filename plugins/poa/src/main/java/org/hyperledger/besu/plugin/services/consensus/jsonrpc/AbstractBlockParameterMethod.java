/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugin.services.consensus.jsonrpc;

import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;

import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

/** Placeholder */
public abstract class AbstractBlockParameterMethod implements JsonRpcMethod {

  /** Placeholder */
  protected final Supplier<BlockchainService> blockchainServiceSupplier;

  /**
   * Placeholder
   *
   * @param blockchain Placeholder
   */
  protected AbstractBlockParameterMethod(final BlockchainService blockchain) {
    this(Suppliers.ofInstance(blockchain));
  }

  /**
   * Placeholder
   *
   * @param blockchainServiceSupplier Placeholder
   */
  protected AbstractBlockParameterMethod(
      final Supplier<BlockchainService> blockchainServiceSupplier) {
    this.blockchainServiceSupplier = blockchainServiceSupplier;
  }

  /**
   * Placeholder
   *
   * @return Placeholder
   */
  protected BlockchainService getBlockchain() {
    return blockchainServiceSupplier.get();
  }

  /**
   * Placeholder
   *
   * @param request Placeholder
   * @return Placeholder
   */
  protected abstract BlockParameter blockParameter(final PluginRpcRequest request);

  /**
   * Placeholder
   *
   * @param request Placeholder
   * @param blockNumber Placeholder
   * @return Placeholder
   */
  protected abstract Object resultByBlockNumber(PluginRpcRequest request, long blockNumber);

  /**
   * Placeholder
   *
   * @param request placholder
   * @return placeholder
   */
  protected Object latestResult(final PluginRpcRequest request) {
    return resultByBlockNumber(
        request, blockchainServiceSupplier.get().getChainHeadHeader().getNumber());
  }

  /**
   * Placeholder
   *
   * @param request Placeholder
   * @return Placeholder
   */
  protected Object findResultByParamType(final PluginRpcRequest request) {
    final BlockParameter blockParam = blockParameter(request);
    final Optional<Long> blockNumber = blockParam.getNumber();

    if (blockNumber.isPresent()) {
      return resultByBlockNumber(request, blockNumber.get());
    } else if (blockParam.isLatest()) {
      return latestResult(request);
    }

    // TODO - safe, finalized and pending not currently supported through plugin API

    return new JsonRpcErrorResponse("Block not found");
  }

  @Override
  public String response(final PluginRpcRequest requestContext) {
    Object response = findResultByParamType(requestContext);

    if (response instanceof JsonRpcErrorResponse) {
      return ((JsonRpcErrorResponse) response).getErrorResponse();
    } else if (response == null) {
      return "null";
    }

    return response.toString();
  }
}
