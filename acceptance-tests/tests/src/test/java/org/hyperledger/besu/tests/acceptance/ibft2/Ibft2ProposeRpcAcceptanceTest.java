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
package org.hyperledger.besu.tests.acceptance.ibft2;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;

import org.junit.Test;

// These tests prove the ibft_proposeValidatorVote and ibft_getValidatorsByBlockNumber (implicitly)
// JSON RPC calls.
public class Ibft2ProposeRpcAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void validatorsCanBeAddedAndThenRemoved() throws IOException {
    final String[] validators = {"validator1", "validator2", "validator3"};
    final BesuNode validator1 = besu.createIbft2NodeWithValidators("validator1", validators);
    final BesuNode validator2 = besu.createIbft2NodeWithValidators("validator2", validators);
    final BesuNode validator3 = besu.createIbft2NodeWithValidators("validator3", validators);
    final BesuNode nonValidatorNode =
        besu.createIbft2NodeWithValidators("non-validator", validators);
    cluster.start(validator1, validator2, validator3, nonValidatorNode);

    cluster.verify(ibftTwo.validatorsEqual(validator1, validator2, validator3));
    final Condition addedCondition = ibftTwo.awaitValidatorSetChange(validator1);
    validator1.execute(ibftTwoTransactions.createAddProposal(nonValidatorNode));
    validator2.execute(ibftTwoTransactions.createAddProposal(nonValidatorNode));

    cluster.verify(addedCondition);
    cluster.verify(ibftTwo.validatorsEqual(validator1, validator2, validator3, nonValidatorNode));

    final Condition removedCondition = ibftTwo.awaitValidatorSetChange(validator1);
    validator2.execute(ibftTwoTransactions.createRemoveProposal(nonValidatorNode));
    validator3.execute(ibftTwoTransactions.createRemoveProposal(nonValidatorNode));
    nonValidatorNode.execute(ibftTwoTransactions.createRemoveProposal(nonValidatorNode));
    cluster.verify(removedCondition);
    cluster.verify(ibftTwo.validatorsEqual(validator1, validator2, validator3));
  }
}
