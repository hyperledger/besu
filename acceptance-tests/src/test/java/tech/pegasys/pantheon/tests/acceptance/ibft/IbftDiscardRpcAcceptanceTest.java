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
package tech.pegasys.pantheon.tests.acceptance.ibft;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import java.io.IOException;

import org.junit.Test;

public class IbftDiscardRpcAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldDiscardVotes() throws IOException {
    final String[] validators = {"validator1", "validator2"};
    final PantheonNode validator1 = pantheon.createIbftNodeWithValidators("validator1", validators);
    final PantheonNode validator2 = pantheon.createIbftNodeWithValidators("validator2", validators);
    final PantheonNode validator3 = pantheon.createIbftNodeWithValidators("validator3", validators);
    cluster.start(validator1, validator2, validator3);

    validator1.execute(ibftTransactions.createRemoveProposal(validator2));
    validator1.execute(ibftTransactions.createAddProposal(validator3));

    validator2.execute(ibftTransactions.createRemoveProposal(validator2));
    validator2.execute(ibftTransactions.createAddProposal(validator3));

    validator1.execute(ibftTransactions.createDiscardProposal(validator2));
    validator1.execute(ibftTransactions.createDiscardProposal(validator3));

    validator1.waitUntil(wait.chainHeadHasProgressedByAtLeast(validator1, 2));

    cluster.verify(ibft.validatorsEqual(validator1, validator2));
    validator1.verify(ibft.noProposals());
    validator2.verify(
        ibft.pendingVotesEqual().removeProposal(validator2).addProposal(validator3).build());
  }
}
