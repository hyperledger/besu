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
package org.hyperledger.besu.consensus.common.jsonrpc.methods.bft;

import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.BFT_DISCARD_VALIDATOR_VOTE;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.IBFT_DISCARD_VALIDATOR_VOTE;

import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BftDiscardValidatorVote implements JsonRpcMethod {
  private static final Logger LOG = LogManager.getLogger();
  private final VoteProposer voteProposer;
  private final boolean legacyRpcMethodName;

  public BftDiscardValidatorVote(final VoteProposer voteProposer) {
    this(voteProposer, false);
  }

  public BftDiscardValidatorVote(
      final VoteProposer voteProposer, final boolean legacyRpcMethodName) {
    this.voteProposer = voteProposer;
    this.legacyRpcMethodName = legacyRpcMethodName;
  }

  @Override
  public String getName() {
    return legacyRpcMethodName
        ? IBFT_DISCARD_VALIDATOR_VOTE.getMethodName()
        : BFT_DISCARD_VALIDATOR_VOTE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Address validatorAddress = requestContext.getRequiredParameter(0, Address.class);
    LOG.trace("Received RPC rpcName={} address={}", getName(), validatorAddress);
    voteProposer.discard(validatorAddress);

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), true);
  }
}
