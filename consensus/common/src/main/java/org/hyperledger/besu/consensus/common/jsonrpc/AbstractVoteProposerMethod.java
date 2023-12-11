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
package org.hyperledger.besu.consensus.common.jsonrpc;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.VoteType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

import java.util.Map;
import java.util.stream.Collectors;

/** The Abstract vote proposer method. */
public class AbstractVoteProposerMethod {

  private final ValidatorProvider validatorProvider;

  /**
   * Instantiates a new Abstract vote proposer method.
   *
   * @param validatorProvider the validator provider
   */
  public AbstractVoteProposerMethod(final ValidatorProvider validatorProvider) {
    this.validatorProvider = validatorProvider;
  }

  /**
   * Response.
   *
   * @param requestContext the request context
   * @return the json rpc response
   */
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (validatorProvider.getVoteProviderAtHead().isPresent()) {
      final Map<String, Boolean> proposals =
          validatorProvider.getVoteProviderAtHead().get().getProposals().entrySet().stream()
              .collect(
                  Collectors.toMap(
                      proposal -> proposal.getKey().toString(),
                      proposal -> proposal.getValue() == VoteType.ADD));

      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), proposals);
    } else {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.METHOD_NOT_ENABLED);
    }
  }
}
