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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.ibft2;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

public class Ibft2Transactions {
  public static final String LATEST = "latest";

  public Ibft2Propose createRemoveProposal(final BesuNode node) {
    return propose(node.getAddress().toString(), false);
  }

  public Ibft2Propose createAddProposal(final BesuNode node) {
    return propose(node.getAddress().toString(), true);
  }

  public Ibft2Proposals createProposals() {
    return new Ibft2Proposals();
  }

  public Ibft2GetValidators createGetValidators(final String blockNumber) {
    return new Ibft2GetValidators(blockNumber);
  }

  public Ibft2GetValidatorsAtHash createGetValidatorsAtHash(final Hash blockHash) {
    return new Ibft2GetValidatorsAtHash(blockHash);
  }

  public Ibft2Discard createDiscardProposal(final BesuNode node) {
    return new Ibft2Discard(node.getAddress().toString());
  }

  private Ibft2Propose propose(final String address, final boolean auth) {
    return new Ibft2Propose(address, auth);
  }
}
