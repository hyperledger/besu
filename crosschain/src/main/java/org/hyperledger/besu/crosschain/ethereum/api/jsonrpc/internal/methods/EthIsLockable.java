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
package org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AbstractBlockParameterMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.core.Address;

public class EthIsLockable extends AbstractBlockParameterMethod {
  public EthIsLockable(final BlockchainQueries blockchain, final JsonRpcParameter parameters) {
    super(blockchain, parameters);
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_IS_LOCKABLE.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return getParameters().required(request.getParams(), 1, BlockParameter.class);
  }

  @Override
  protected String resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    final Address address = getParameters().required(request.getParams(), 0, Address.class);
    return getBlockchainQueries()
        .isContractLockable(address, blockNumber)
        .map(Quantity::create)
        .orElse(null);
  }
}
