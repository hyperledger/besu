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
package tech.pegasys.pantheon.consensus.clique.jsonrpc.methods;

import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class Discard implements JsonRpcMethod {
  private static final String CLIQUE_DISCARD = "clique_discard";
  private final VoteProposer proposer;
  private final JsonRpcParameter parameters;

  public Discard(final VoteProposer proposer, final JsonRpcParameter parameters) {
    this.proposer = proposer;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return CLIQUE_DISCARD;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final Address address = parameters.required(request.getParams(), 0, Address.class);
    proposer.discard(address);
    return new JsonRpcSuccessResponse(request.getId(), true);
  }
}
