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
package org.hyperledger.besu.tests.acceptance.bft;

import static java.util.Collections.singletonList;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.Test;

public class BftBlockRewardPaymentAcceptanceTest extends ParameterizedBftTestBase {

  public BftBlockRewardPaymentAcceptanceTest(
      final String testName, final BftAcceptanceTestParameterization nodeFactory) {
    super(testName, nodeFactory);
  }

  @Test
  public void validatorsArePaidBlockReward() throws Exception {
    final String[] validators = {"validator"};
    final BesuNode validator = nodeFactory.createNodeWithValidators(besu, "validator", validators);
    final BesuNode nonValidator =
        nodeFactory.createNodeWithValidators(besu, "nonValidator", validators);
    cluster.start(validator, nonValidator);
    final Account validator1Account = Account.create(ethTransactions, validator.getAddress());

    final int blockRewardEth = 5;
    final int blockToCheck = 2;

    cluster.verify(validator1Account.balanceAtBlockEquals(Amount.ether(0), BigInteger.ZERO));
    cluster.verify(
        validator1Account.balanceAtBlockEquals(
            Amount.ether(blockRewardEth * blockToCheck), BigInteger.valueOf(blockToCheck)));
  }

  @Test
  public void payBlockRewardToConfiguredNode() throws Exception {
    final String[] validators = {"validator1"};
    final BesuNode validator1 =
        nodeFactory.createNodeWithValidators(besu, "validator1", validators);
    final Optional<String> initialConfig =
        validator1.getGenesisConfigProvider().create(singletonList(validator1));
    if (initialConfig.isEmpty()) {
      throw new RuntimeException("Unable to generate genesis config.");
    }
    final String miningBeneficiaryAddress = "0x1234567890123456789012345678901234567890";

    final String configWithMiningBeneficiary =
        initialConfig
            .get()
            .replace(
                "\"" + bftType + "\": {",
                "\""
                    + bftType
                    + "\": { \"miningbeneficiary\": \""
                    + miningBeneficiaryAddress
                    + "\",");

    validator1.setGenesisConfig(configWithMiningBeneficiary);

    final Account miningBeneficiaryAccount =
        Account.create(ethTransactions, Address.fromHexString(miningBeneficiaryAddress));

    // This starts a node, without executing its configGenerator
    cluster.start(validator1);
    final int blockRewardEth = 5;
    final int blockToCheck = 2;

    cluster.verify(miningBeneficiaryAccount.balanceAtBlockEquals(Amount.ether(0), BigInteger.ZERO));
    cluster.verify(
        miningBeneficiaryAccount.balanceAtBlockEquals(
            Amount.ether(blockRewardEth * blockToCheck), BigInteger.valueOf(blockToCheck)));
  }
}
