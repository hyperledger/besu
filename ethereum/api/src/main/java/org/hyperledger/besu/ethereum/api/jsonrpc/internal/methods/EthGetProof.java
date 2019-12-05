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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.proof.GetProofResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class EthGetProof extends AbstractBlockParameterMethod {

  private final BlockchainQueries blockchain;

  public EthGetProof(final BlockchainQueries blockchain) {
    super(blockchain);
    this.blockchain = blockchain;
  }

  private Address getAddress(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(0, Address.class);
  }

  private List<UInt256> getStorageKeys(final JsonRpcRequestContext request) {
    return Arrays.stream(request.getRequiredParameter(1, String[].class))
        .map(UInt256::fromHexString)
        .collect(Collectors.toList());
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(2, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext requestContext, final long blockNumber) {

    final Address address = getAddress(requestContext);
    final List<UInt256> storageKeys = getStorageKeys(requestContext);

    final Optional<MutableWorldState> worldState = blockchain.getWorldState(blockNumber);

    if (worldState.isPresent()) {
      Optional<WorldStateProof> proofOptional =
          blockchain
              .getWorldStateArchive()
              .getAccountProof(worldState.get().rootHash(), address, storageKeys);
      return proofOptional
          .map(
              proof ->
                  (JsonRpcResponse)
                      new JsonRpcSuccessResponse(
                          requestContext.getRequest().getId(),
                          GetProofResult.buildGetProofResult(address, proof)))
          .orElse(
              new JsonRpcErrorResponse(
                  requestContext.getRequest().getId(), JsonRpcError.NO_ACCOUNT_FOUND));
    }

    return new JsonRpcErrorResponse(
        requestContext.getRequest().getId(), JsonRpcError.WORLD_STATE_UNAVAILABLE);
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return (JsonRpcResponse) findResultByParamType(requestContext);
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_PROOF.getMethodName();
  }
}
