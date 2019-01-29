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
package tech.pegasys.pantheon.tests.acceptance.dsl.waitcondition;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;
import static tech.pegasys.pantheon.tests.acceptance.dsl.transaction.clique.CliqueTransactions.LATEST;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ibft.IbftTransactions;

import java.util.List;

public class WaitUntilValidatorsChanged implements WaitCondition {

  private final IbftTransactions ibft;
  private final List<Address> initialSigners;

  public WaitUntilValidatorsChanged(
      final List<Address> initialSigners, final IbftTransactions ibft) {
    this.initialSigners = initialSigners;
    this.ibft = ibft;
  }

  @Override
  public void waitUntil(final Node node) {
    waitFor(
        60,
        () ->
            assertThat(node.execute(ibft.createGetValidators(LATEST)))
                .isNotEqualTo(initialSigners));
  }
}
