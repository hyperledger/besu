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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.UInt256Parameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.util.uint.UInt256;

public class EthGetStorageAt extends AbstractBlockParameterMethod {

  public EthGetStorageAt(final BlockchainQueries blockchain, final JsonRpcParameter parameters) {
    super(blockchain, parameters);
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_STORAGE_AT.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return getParameters().required(request.getParams(), 2, BlockParameter.class);
  }

  @Override
  protected String resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    final Address address = getParameters().required(request.getParams(), 0, Address.class);
    final UInt256 position =
        getParameters().required(request.getParams(), 1, UInt256Parameter.class).getValue();
    return getBlockchainQueries()
        .storageAt(address, position, blockNumber)
        .map(UInt256::toHexString)
        .orElse(null);
  }
}
