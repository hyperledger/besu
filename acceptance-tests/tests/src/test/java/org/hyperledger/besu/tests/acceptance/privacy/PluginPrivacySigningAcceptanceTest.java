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
package org.hyperledger.besu.tests.acceptance.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver.BOB;

import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.privacy.PrivacyNodeConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveKeyConfiguration;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class PluginPrivacySigningAcceptanceTest extends PrivacyAcceptanceTestBase {
  private PrivacyNode minerNode;

  @Before
  public void setup() throws IOException {
    minerNode =
        privacyBesu.create(
            new PrivacyNodeConfiguration(
                false,
                false,
                true,
                new BesuNodeConfigurationBuilder()
                    .name("miner")
                    .miningEnabled()
                    .jsonRpcEnabled()
                    .webSocketEnabled()
                    .enablePrivateTransactions()
                    .keyFilePath(BOB.getPrivateKeyPath())
                    .plugins(Collections.singletonList("testPlugins"))
                    .extraCLIOptions(
                        List.of(
                            "--plugin-privacy-service-encryption-prefix=0xAA",
                            "--plugin-privacy-service-signing-enabled=true",
                            "--plugin-privacy-service-signing-key=8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63"))
                    .build(),
                new EnclaveKeyConfiguration(
                    BOB.getEnclaveKeyPaths(), BOB.getEnclavePrivateKeyPaths())),
            EnclaveType.NOOP,
            Optional.empty());

    privacyCluster.start(minerNode);
  }

  @Test
  public void canDeployContractSignedByPlugin() throws Exception {
    final String contractAddress = "0xd0152772c54cecfa7684f09f7616dcc825545dff";

    final EventEmitter eventEmitter =
        minerNode.execute(
            privateContractTransactions.createSmartContract(
                EventEmitter.class,
                minerNode.getTransactionSigningKey(),
                minerNode.getEnclaveKey()));

    privateContractVerifier
        .validPrivateContractDeployed(contractAddress, minerNode.getAddress().toString())
        .verify(eventEmitter);
    privateContractVerifier.validContractCodeProvided().verify(eventEmitter);

    final BigInteger blockNumberContractDeployed =
        eventEmitter.getTransactionReceipt().get().getBlockNumber();
    final Block blockContractDeployed =
        minerNode.execute(
            ethTransactions.block(DefaultBlockParameter.valueOf(blockNumberContractDeployed)));

    assertThat(blockContractDeployed.getTransactions().size()).isEqualTo(1);

    final String transactionHashContractDeployed =
        (String) blockContractDeployed.getTransactions().get(0).get();
    final TransactionReceipt pmtReceipt =
        minerNode
            .execute(ethTransactions.getTransactionReceipt(transactionHashContractDeployed))
            .get();

    assertThat(pmtReceipt.getStatus()).isEqualTo("0x1");
    assertThat(pmtReceipt.getFrom()).isEqualTo("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
  }
}
