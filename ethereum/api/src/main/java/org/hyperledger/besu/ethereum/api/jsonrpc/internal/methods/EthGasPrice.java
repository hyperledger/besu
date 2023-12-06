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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;

import java.util.Optional;
import java.util.function.Supplier;

public class EthGasPrice implements JsonRpcMethod {

  private final Supplier<BlockchainQueries> blockchain;
  private final MiningCoordinator miningCoordinator;
  private final ApiConfiguration apiConfiguration;

  public EthGasPrice(
      final BlockchainQueries blockchain,
      final MiningCoordinator miningCoordinator,
      final ApiConfiguration apiConfiguration) {
    this(() -> blockchain, miningCoordinator, apiConfiguration);
  }

  public EthGasPrice(
      final Supplier<BlockchainQueries> blockchain,
      final MiningCoordinator miningCoordinator,
      final ApiConfiguration apiConfiguration) {
    this.blockchain = blockchain;
    this.miningCoordinator = miningCoordinator;
    this.apiConfiguration = apiConfiguration;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GAS_PRICE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), Quantity.create(calculateGasPrice()));
  }

  private Wei calculateGasPrice() {
    Wei gasPrice = getGasPrice().orElseGet(miningCoordinator::getMinTransactionGasPrice);
    return isGasPriceLimitingEnabled() ? limitGasPrice(gasPrice) : gasPrice;
  }

  private Optional<Wei> getGasPrice() {
    return blockchain.get().gasPrice().map(Wei::of);
  }

  private boolean isGasPriceLimitingEnabled() {
    return apiConfiguration.isGasAndPriorityFeeLimitingEnabled();
  }

  private Wei limitGasPrice(final Wei gasPrice) {
    Wei minTransactionGasPrice = miningCoordinator.getMinTransactionGasPrice();
    Wei lowerBound =
        calculateBound(
            minTransactionGasPrice, apiConfiguration.getLowerBoundGasAndPriorityFeeCoefficient());
    Wei upperBound =
        calculateBound(
            minTransactionGasPrice, apiConfiguration.getUpperBoundGasAndPriorityFeeCoefficient());

    return (gasPrice.compareTo(lowerBound) <= 0)
        ? lowerBound
        : (gasPrice.compareTo(upperBound) > 0) ? upperBound : gasPrice;
  }

  private Wei calculateBound(final Wei price, final long coefficient) {
    return price.multiply(coefficient).divide(100);
  }
}
