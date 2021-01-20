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

import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.BFT_GET_PENDING_VOTES;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.IBFT_GET_PENDING_VOTES;

import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.common.jsonrpc.AbstractVoteProposerMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;

public class BftGetPendingVotes extends AbstractVoteProposerMethod implements JsonRpcMethod {
  private final boolean legacyRpcMethodName;

  public BftGetPendingVotes(final VoteProposer voteProposer) {
    this(voteProposer, false);
  }

  public BftGetPendingVotes(final VoteProposer voteProposer, final boolean legacyRpcMethodName) {
    super(voteProposer);
    this.legacyRpcMethodName = legacyRpcMethodName;
  }

  @Override
  public String getName() {
    return legacyRpcMethodName
        ? IBFT_GET_PENDING_VOTES.getMethodName()
        : BFT_GET_PENDING_VOTES.getMethodName();
  }
}
