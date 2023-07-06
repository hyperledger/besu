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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.bft;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

public class BftTransactions {
  public static final String LATEST = "latest";

  public BftPropose createRemoveProposal(final BesuNode node) {
    return propose(node.getAddress().toString(), false);
  }

  public BftPropose createAddProposal(final BesuNode node) {
    return propose(node.getAddress().toString(), true);
  }

  public BftProposals createProposals() {
    return new BftProposals();
  }

  public BftGetValidators createGetValidators(final String blockNumber) {
    return new BftGetValidators(blockNumber);
  }

  public BftGetValidatorsAtHash createGetValidatorsAtHash(final Hash blockHash) {
    return new BftGetValidatorsAtHash(blockHash);
  }

  public BftDiscard createDiscardProposal(final BesuNode node) {
    return new BftDiscard(node.getAddress().toString());
  }

  private BftPropose propose(final String address, final boolean auth) {
    return new BftPropose(address, auth);
  }
}
