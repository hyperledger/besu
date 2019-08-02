/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods;

import tech.pegasys.pantheon.consensus.common.BlockInterface;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.AbstractBlockParameterMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;

import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IbftGetValidatorsByBlockNumber extends AbstractBlockParameterMethod
    implements JsonRpcMethod {
  private static final Logger LOG = LogManager.getLogger();

  private final BlockInterface blockInterface;

  public IbftGetValidatorsByBlockNumber(
      final BlockchainQueries blockchainQueries,
      final BlockInterface blockInterface,
      final JsonRpcParameter parameters) {
    super(blockchainQueries, parameters);
    this.blockInterface = blockInterface;
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return getParameters().required(request.getParams(), 0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    final Optional<BlockHeader> blockHeader =
        getBlockchainQueries().getBlockHeaderByNumber(blockNumber);
    LOG.trace("Received RPC rpcName={} block={}", getName(), blockNumber);
    return blockHeader
        .map(
            header ->
                blockInterface.validatorsInBlock(header).stream()
                    .map(validator -> validator.toString())
                    .collect(Collectors.toList()))
        .orElse(null);
  }

  @Override
  public String getName() {
    return RpcMethod.IBFT_GET_VALIDATORS_BY_BLOCK_NUMBER.getMethodName();
  }
}
