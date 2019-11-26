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
package org.hyperledger.besu.tests.acceptance.crosschain.lockability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;

import org.hyperledger.besu.tests.acceptance.crosschain.CrosschainAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.SimpleIsLockable;
import org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.SimpleIsLockableCrosschain;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.crosschain.CrossIsLockableTransaction;

import org.junit.Before;
import org.junit.Test;

public class IsLockableAcceptanceTest extends CrosschainAcceptanceTestBase {
  @Before
  public void setUp() throws Exception {
    setUpCoordiantionChain();
    setUpBlockchain1();
  }

  @Test
  public void normalDeployShouldBeNotLockable() {
    final SimpleIsLockable simpleContract =
        this.nodeOnBlockchain1.execute(
            contractTransactions.createSmartContract(SimpleIsLockable.class));
    final String contractAddress = simpleContract.getContractAddress();

    CrossIsLockableTransaction isLockableObj = crossTransactions.getIsLockable(contractAddress);
    boolean isLockable = nodeOnBlockchain1.execute(isLockableObj);

    assertThat(isLockable).isFalse();
  }

  @Test
  public void lockableDeployShouldBeLockable() {
    final SimpleIsLockableCrosschain simpleContract =
        nodeOnBlockchain1.execute(
            contractTransactions.createLockableSmartContract(
                SimpleIsLockableCrosschain.class, this.transactionManagerBlockchain1));
    final String contractAddress = simpleContract.getContractAddress();

    CrossIsLockableTransaction isLockableObj = crossTransactions.getIsLockable(contractAddress);
    boolean isLockable = nodeOnBlockchain1.execute(isLockableObj);

    assertThat(isLockable).isTrue();
  }
}
