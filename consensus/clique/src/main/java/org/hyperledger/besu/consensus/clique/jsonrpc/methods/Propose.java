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
package org.hyperledger.besu.consensus.clique.jsonrpc.methods;

import org.hyperledger.besu.consensus.clique.CliqueBlockInterface;
import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;

public class Propose implements JsonRpcMethod {
  private final VoteProposer proposer;
  private final JsonRpcParameter parameters;

  public Propose(final VoteProposer proposer, final JsonRpcParameter parameters) {
    this.proposer = proposer;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.CLIQUE_PROPOSE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final Address address = parameters.required(request.getParams(), 0, Address.class);
    final Boolean auth = parameters.required(request.getParams(), 1, Boolean.class);
    if (address.equals(CliqueBlockInterface.NO_VOTE_SUBJECT)) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_REQUEST);
    }

    if (auth) {
      proposer.auth(address);
    } else {
      proposer.drop(address);
    }
    // Return true regardless, the vote is always recorded
    return new JsonRpcSuccessResponse(request.getId(), true);
  }
}
