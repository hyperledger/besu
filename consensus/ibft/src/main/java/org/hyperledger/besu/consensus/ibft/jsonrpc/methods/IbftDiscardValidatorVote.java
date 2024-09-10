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
package org.hyperledger.besu.consensus.ibft.jsonrpc.methods;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Ibft discard validator vote. */
public class IbftDiscardValidatorVote implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(IbftDiscardValidatorVote.class);
  private final ValidatorProvider validatorProvider;

  /**
   * Instantiates a new Ibft discard validator vote.
   *
   * @param validatorProvider the validator provider
   */
  public IbftDiscardValidatorVote(final ValidatorProvider validatorProvider) {
    this.validatorProvider = validatorProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.IBFT_DISCARD_VALIDATOR_VOTE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    checkState(
        validatorProvider.getVoteProviderAtHead().isPresent(), "Ibft requires a vote provider");
    final Address validatorAddress;
    try {
      validatorAddress = requestContext.getRequiredParameter(0, Address.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid validator address parameter (index 0)", RpcErrorType.INVALID_ADDRESS_PARAMS, e);
    }
    LOG.trace("Received RPC rpcName={} address={}", getName(), validatorAddress);
    validatorProvider.getVoteProviderAtHead().get().discardVote(validatorAddress);

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), true);
  }
}
