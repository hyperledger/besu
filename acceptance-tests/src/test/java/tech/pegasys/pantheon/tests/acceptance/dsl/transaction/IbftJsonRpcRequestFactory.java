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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ResponseTypes.DiscardResponse;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ResponseTypes.ProposalsResponse;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ResponseTypes.ProposeResponse;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ResponseTypes.SignersBlockResponse;

import java.util.Arrays;

import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;

public class IbftJsonRpcRequestFactory {

  private final Web3jService web3jService;

  public IbftJsonRpcRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
  }

  public Request<?, ProposeResponse> ibftPropose(final String address, final Boolean auth) {
    return new Request<>(
        "ibft_proposeValidatorVote",
        Arrays.asList(address, auth.toString()),
        web3jService,
        ProposeResponse.class);
  }

  public Request<?, DiscardResponse> ibftDiscard(final String address) {
    return new Request<>(
        "ibft_discardValidatorVote", singletonList(address), web3jService, DiscardResponse.class);
  }

  public Request<?, ProposalsResponse> ibftProposals() {
    return new Request<>(
        "ibft_getPendingVotes", emptyList(), web3jService, ProposalsResponse.class);
  }

  public Request<?, SignersBlockResponse> ibftGetValidators(final String blockNumber) {
    return new Request<>(
        "ibft_getValidatorsByBlockNumber",
        singletonList(blockNumber),
        web3jService,
        SignersBlockResponse.class);
  }

  public Request<?, SignersBlockResponse> ibftGetSignersAtHash(final Hash hash) {
    return new Request<>(
        "ibft_getValidatorsByBlockHash",
        singletonList(hash.toString()),
        web3jService,
        SignersBlockResponse.class);
  }
}
