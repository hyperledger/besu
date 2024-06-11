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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;

import java.util.Optional;

public class EthMaxPriorityFeePerGas implements JsonRpcMethod {

  private final BlockchainQueries blockchainQueries;
  private final MiningCoordinator miningCoordinator;

  public EthMaxPriorityFeePerGas(
      final BlockchainQueries blockchainQueries, final MiningCoordinator miningCoordinator) {
    this.blockchainQueries = blockchainQueries;
    this.miningCoordinator = miningCoordinator;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_MAX_PRIORITY_FEE_PER_GAS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), Quantity.create(fetchAndLimitPriorityFeePerGas()));
  }

  private Wei fetchAndLimitPriorityFeePerGas() {
    final Optional<Wei> gasPrice = blockchainQueries.gasPriorityFee();
    return gasPrice.orElseGet(miningCoordinator::getMinPriorityFeePerGas);
  }
}
