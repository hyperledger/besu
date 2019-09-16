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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class EthGetCode extends AbstractBlockParameterMethod {

  public EthGetCode(final BlockchainQueries blockchainQueries, final JsonRpcParameter parameters) {
    super(Suppliers.ofInstance(blockchainQueries), parameters);
  }

  public EthGetCode(
      final Supplier<BlockchainQueries> blockchainQueries, final JsonRpcParameter parameters) {
    super(blockchainQueries, parameters);
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_CODE.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return getParameters().required(request.getParams(), 1, BlockParameter.class);
  }

  @Override
  protected String resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    final Address address = getParameters().required(request.getParams(), 0, Address.class);
    return getBlockchainQueries()
        .getCode(address, blockNumber)
        .map(BytesValue::toString)
        .orElse(null);
  }
}
