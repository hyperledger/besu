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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.web3j.generated.permissioning.PrivacyGroup;
import org.hyperledger.besu.tests.web3j.generated.permissioning.PrivacyProxy;
import org.hyperledger.besu.tests.web3j.generated.permissioning.UpgradedPrivacyGroup;
import org.hyperledger.besu.util.bytes.Bytes32;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.web3j.tx.exceptions.ContractCallException;

public class DeployPermissioningContractAcceptanceTest extends AcceptanceTestBase {

  private BesuNode minerNode;
  private PrivacyProxy privacyProxy;
  private UpgradedPrivacyGroup upgradedPrivacyGroup;

  private byte[] firstMemberKey =
      Bytes32.fromHexString("357c2fd7f6bec286163f99bf8324345d3e2a85285b3baacdd3d80a3b8648b734")
          .getByteArray();
  private byte[] secondMemberKey =
      Bytes32.fromHexString("357c2fd7f6bec286163f99bf8324345d3e2a85285b3baacdd3d80a3b8648b735")
          .getByteArray();

  private byte[] unauthorizedMemberKey =
      Bytes32.fromHexString("357c2fd7f6bec286163f99bf8324345d3e2a85285b3baacdd3d80a3b8648b736")
          .getByteArray();

  @Before
  public void setUp() throws Exception {
    minerNode = besu.createMinerNode("miner-node");
    cluster.start(minerNode);

    final PrivacyGroup privacyGroup = minerNode.execute(
            contractTransactions.createSmartContract(
                    PrivacyGroup.class, firstMemberKey, new ArrayList<byte[]>(), "Name", "Description"));
    final String contractAddress = "0x42699a7612a82f1d9c36148af9c77354759b210b";
    contractVerifier.validTransactionReceipt(contractAddress).verify(privacyGroup);
    privacyProxy =
        minerNode.execute(
            contractTransactions.createSmartContract(PrivacyProxy.class, contractAddress));
    privacyGroup.setProxy(firstMemberKey, privacyProxy.getContractAddress()).send();

    upgradedPrivacyGroup =
            minerNode.execute(
                    contractTransactions.createSmartContract(
                            UpgradedPrivacyGroup.class,
                            firstMemberKey,
                            new ArrayList<byte[]>(),
                            "Name",
                            "Description"));
  }

  @Test
  public void deployingProxyMustReturnValidPrivacyContractAddress() throws Exception {
    assertThat(privacyProxy.getParticipants(firstMemberKey).send().size()).isEqualTo(1);
    privacyProxy.addParticipants(firstMemberKey, Collections.singletonList(secondMemberKey)).send();
    assertThat(privacyProxy.getParticipants(firstMemberKey).send().size()).isEqualTo(2);
  }

  @Test
  public void upgradingPrivacyContractMustWork() throws Exception {
    privacyProxy.upgradeTo(upgradedPrivacyGroup.getContractAddress()).send();
    upgradedPrivacyGroup.setProxy(firstMemberKey, privacyProxy.getContractAddress()).send();

    privacyProxy.addParticipants(firstMemberKey, Collections.singletonList(secondMemberKey)).send();


    assertThat(privacyProxy.getParticipants(firstMemberKey).send().size()).isEqualTo(2);
    upgradedPrivacyGroup.removeParticipant(firstMemberKey, secondMemberKey).send();
    assertThat(privacyProxy.getParticipants(firstMemberKey).send().size()).isEqualTo(1);
  }

  @Test
  public void unauthorizedPersonsShouldBeUnableToAccessContract() throws Exception {
    assertThatThrownBy(() -> privacyProxy.getParticipants(unauthorizedMemberKey).send())
        .isInstanceOf(ContractCallException.class);
  }
}
