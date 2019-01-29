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

public class CliqueJsonRpcRequestFactory {

  private final Web3jService web3jService;

  public CliqueJsonRpcRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
  }

  public Request<?, ProposeResponse> cliquePropose(final String address, final Boolean auth) {
    return new Request<>(
        "clique_propose",
        Arrays.asList(address, auth.toString()),
        web3jService,
        ProposeResponse.class);
  }

  public Request<?, DiscardResponse> cliqueDiscard(final String address) {
    return new Request<>(
        "clique_discard", singletonList(address), web3jService, DiscardResponse.class);
  }

  public Request<?, ProposalsResponse> cliqueProposals() {
    return new Request<>("clique_proposals", emptyList(), web3jService, ProposalsResponse.class);
  }

  public Request<?, SignersBlockResponse> cliqueGetSigners(final String blockNumber) {
    return new Request<>(
        "clique_getSigners", singletonList(blockNumber), web3jService, SignersBlockResponse.class);
  }

  public Request<?, SignersBlockResponse> cliqueGetSignersAtHash(final Hash hash) {
    return new Request<>(
        "clique_getSignersAtHash",
        singletonList(hash.toString()),
        web3jService,
        SignersBlockResponse.class);
  }
}
