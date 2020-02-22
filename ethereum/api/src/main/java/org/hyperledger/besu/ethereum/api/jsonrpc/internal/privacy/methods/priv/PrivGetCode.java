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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AbstractBlockParameterMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivGetCode extends AbstractBlockParameterMethod {

  private final BlockchainQueries blockchain;
  private final PrivateStateRootResolver privateStateRootResolver;
  private final WorldStateArchive privateWorldStateArchive;

  public PrivGetCode(
      final BlockchainQueries blockchainQueries,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver) {
    super(blockchainQueries);
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.blockchain = blockchainQueries;
    this.privateStateRootResolver = privateStateRootResolver;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_CODE.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(2, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    final String privacyGroupId = request.getRequiredParameter(0, String.class);

    final Address address = Address.fromHexString(request.getRequiredParameter(1, String.class));

    final Hash latestStateRoot =
        privateStateRootResolver.resolveLastStateRoot(
            Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)),
            blockchain.getBlockchain().getBlockByNumber(blockNumber).orElseThrow().getHash());

    return privateWorldStateArchive
        .get(latestStateRoot)
        .flatMap(
            pws ->
                Optional.ofNullable(pws.get(address)).map(account -> account.getCode().toString()))
        .map(c -> new JsonRpcSuccessResponse(request.getRequest().getId(), c))
        .orElse(new JsonRpcSuccessResponse(request.getRequest().getId(), null));
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return (JsonRpcResponse) findResultByParamType(requestContext);
  }
}
