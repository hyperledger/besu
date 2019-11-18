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
package org.hyperledger.besu.tests.web3j.permissioning;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import org.hyperledger.besu.tests.web3j.generated.permissioning.PrivacyGroup;
import org.hyperledger.besu.tests.web3j.generated.permissioning.PrivacyProxy;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class DeployPermissioningContractAcceptanceTest extends AcceptanceTestBase {

  private BesuNode minerNode;

  @Before
  public void setUp() throws Exception {
    minerNode = besu.createMinerNode("miner-node");
    cluster.start(minerNode);
  }



  @Test
  public void deployingProxyMustReturnValidPrivacyContractAddress() throws Exception {
    final String contractAddress = "0x42699a7612a82f1d9c36148af9c77354759b210b";

    final PrivacyGroup privacyGroup =
        minerNode.execute(contractTransactions.createSmartContract(PrivacyGroup.class));
    contractVerifier.validTransactionReceipt(contractAddress).verify(privacyGroup);

    final PrivacyProxy privacyProxy = minerNode.execute(contractTransactions.createSmartContract(PrivacyProxy.class, contractAddress));
    privacyGroup.setProxy(privacyProxy.getContractAddress()).send();

    assertThat(privacyProxy.getParticipants().send().size()).isEqualTo(1);
    privacyProxy.addParticipants(Collections.singletonList("0xceeeefe21b2f2ea5df62ed2efde1e3f1e5540f96")).send();
    assertThat(privacyProxy.getParticipants().send().size()).isEqualTo(2);
  }
}
