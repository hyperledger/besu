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
package org.hyperledger.besu.tests.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.hyperledger.besu.tests.container.helpers.ContractOperations.deployContractAndReturnAddress;
import static org.hyperledger.besu.tests.container.helpers.ContractOperations.generateHexString;
import static org.hyperledger.besu.tests.container.helpers.ContractOperations.getTransactionLog;
import static org.hyperledger.besu.tests.container.helpers.ContractOperations.sendLogEventAndReturnTransactionHash;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.disposables.Disposable;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.quorum.enclave.Enclave;
import org.web3j.quorum.enclave.Tessera;
import org.web3j.quorum.enclave.protocol.EnclaveService;
import org.web3j.quorum.tx.QuorumTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

public class ContainerTests extends ContainerTestBase {

  private Credentials credentials;
  private Enclave besuEnclave;
  private EnclaveService besuEnclaveService;
  private Enclave goQuorumEnclave;
  private EnclaveService goQuorumEnclaveService;
  private PollingTransactionReceiptProcessor besuPollingTransactionReceiptProcessor;
  private PollingTransactionReceiptProcessor goQuorumPollingTransactionReceiptProcessor;

  @Before
  public void testSetUp() throws IOException, CipherException {
    besuEnclaveService =
        new EnclaveService(
            "http://" + tesseraBesuContainer.getHost(),
            tesseraBesuContainer.getMappedPort(tesseraRestPort),
            new OkHttpClient());
    besuEnclave = new Tessera(besuEnclaveService, besuWeb3j);
    besuPollingTransactionReceiptProcessor =
        new PollingTransactionReceiptProcessor(besuWeb3j, 1000, 10);
    goQuorumEnclaveService =
        new EnclaveService(
            "http://" + tesseraGoQuorumContainer.getHost(),
            tesseraGoQuorumContainer.getMappedPort(tesseraRestPort),
            new OkHttpClient());
    goQuorumEnclave = new Tessera(goQuorumEnclaveService, goQuorumWeb3j);
    goQuorumPollingTransactionReceiptProcessor =
        new PollingTransactionReceiptProcessor(goQuorumWeb3j, 1000, 10);
    credentials = loadCredentials();
  }

  @Test
  public void contractShouldBeDeployedToBothNodes() throws IOException, TransactionException {
    // create a GoQuorum transaction manager
    final QuorumTransactionManager qtm =
        new QuorumTransactionManager(
            goQuorumWeb3j,
            credentials,
            goQuorumTesseraPubKey,
            Arrays.asList(goQuorumTesseraPubKey, besuTesseraPubKey),
            goQuorumEnclave);

    // Get the deployed contract address
    final String contractAddress =
        deployContractAndReturnAddress(
            goQuorumWeb3j,
            credentials,
            qtm,
            besuPollingTransactionReceiptProcessor,
            goQuorumPollingTransactionReceiptProcessor);

    // Generate a random value to insert into the log
    final String logValue = generateHexString(98765L);

    // Send the transaction and get the transaction hash
    final String transactionHash =
        sendLogEventAndReturnTransactionHash(
            goQuorumWeb3j,
            credentials,
            contractAddress,
            qtm,
            besuPollingTransactionReceiptProcessor,
            goQuorumPollingTransactionReceiptProcessor,
            logValue);

    // Get the transaction logs
    final String goQuorumResult = getTransactionLog(goQuorumWeb3j, transactionHash);
    final String besuResult = getTransactionLog(besuWeb3j, transactionHash);

    assertThat(besuResult).isEqualTo(logValue);
    assertThat(goQuorumResult).isEqualTo(logValue);
  }

  @Test
  public void contractShouldBeDeployedOnlyToGoQuorumNode()
      throws IOException, TransactionException {
    // create a quorum transaction manager
    final QuorumTransactionManager qtm =
        new QuorumTransactionManager(
            goQuorumWeb3j,
            credentials,
            goQuorumTesseraPubKey,
            List.of(goQuorumTesseraPubKey),
            goQuorumEnclave);

    // Get the deployed contract address
    final String contractAddress =
        deployContractAndReturnAddress(
            goQuorumWeb3j,
            credentials,
            qtm,
            goQuorumPollingTransactionReceiptProcessor,
            besuPollingTransactionReceiptProcessor);

    // Generate a random value to insert into the log
    final String logValue = generateHexString(192837L);

    // Send the transaction and get the transaction hash
    final String transactionHash =
        sendLogEventAndReturnTransactionHash(
            goQuorumWeb3j,
            credentials,
            contractAddress,
            qtm,
            goQuorumPollingTransactionReceiptProcessor,
            besuPollingTransactionReceiptProcessor,
            logValue);

    // Assert the GoQuorum node has received the log
    final String quorumResult = getTransactionLog(goQuorumWeb3j, transactionHash);
    assertThat(quorumResult).isEqualTo(logValue);

    // Assert the Besu node has not received the log
    assertThatThrownBy(() -> getTransactionLog(besuWeb3j, transactionHash))
        .hasMessageContaining("No log found");
  }

  @Test
  public void contractShouldBeDeployedOnlyToBesuNode()
      throws IOException, TransactionException, InterruptedException {
    // create a GoQuorum transaction manager
    final QuorumTransactionManager qtm =
        new QuorumTransactionManager(
            besuWeb3j, credentials, besuTesseraPubKey, List.of(besuTesseraPubKey), besuEnclave);

    // Get the deployed contract address
    final String contractAddress =
        deployContractAndReturnAddress(
            besuWeb3j,
            credentials,
            qtm,
            besuPollingTransactionReceiptProcessor,
            goQuorumPollingTransactionReceiptProcessor);

    // Subscribe to the event
    final Event testEvent =
        new Event(
            "TestEvent",
            Arrays.<TypeReference<?>>asList(
                new TypeReference<Address>(true) {}, new TypeReference<Int256>() {}));
    final String eventEncoded = EventEncoder.encode(testEvent);

    final EthFilter ethFilterSubscription =
        new EthFilter(
            DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST, contractAddress);
    ethFilterSubscription.addSingleTopic(eventEncoded);

    // Generate a value to insert into the log
    final String logValue = generateHexString((1234567L));

    final AtomicBoolean checked = new AtomicBoolean(false);
    final Disposable subscribe =
        besuWeb3j
            .ethLogFlowable(ethFilterSubscription)
            .subscribe(
                log -> {
                  final String eventHash =
                      log.getTopics().get(0); // Index 0 is the event definition hash

                  if (eventHash.equals(eventEncoded)) {
                    // address indexed _arg1
                    final Address arg1 =
                        (Address)
                            FunctionReturnDecoder.decodeIndexedValue(
                                log.getTopics().get(1), new TypeReference<Address>() {});
                    assertThat(arg1.toString()).isEqualTo(credentials.getAddress());

                    final String data = log.getData();
                    assertThat(data.substring(2)).isEqualTo(logValue);

                    checked.set(true);
                  }
                });

    // Send the transaction and get the transaction hash
    final String transactionHash =
        sendLogEventAndReturnTransactionHash(
            besuWeb3j,
            credentials,
            contractAddress,
            qtm,
            besuPollingTransactionReceiptProcessor,
            goQuorumPollingTransactionReceiptProcessor,
            logValue);

    int secondsWaited = 0;
    while (!checked.get()) {
      Thread.sleep(1000);
      secondsWaited++;
      if (secondsWaited > 30) {
        fail("Waited more than 30 seconds for log.");
      }
    }

    subscribe.dispose();

    // Assert the Besu node has received the log
    final String besuResult = getTransactionLog(besuWeb3j, transactionHash);
    assertThat(besuResult).isEqualTo(logValue);

    // Assert the GoQuorum node has not received the log
    assertThatThrownBy(() -> getTransactionLog(goQuorumWeb3j, transactionHash))
        .hasMessageContaining("No log found");

    // Get transaction log using a filter
    final EthFilter ethFilter =
        new EthFilter(
            DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST, contractAddress);
    ethFilter.addSingleTopic(eventEncoded);

    @SuppressWarnings("rawtypes")
    final List<EthLog.LogResult> logs = besuWeb3j.ethGetLogs(ethFilter).send().getLogs();

    assertThat(logs.size()).isEqualTo(1);
    assertThat(logs.toString()).contains(transactionHash);
    assertThat(logs.toString()).contains(logValue);
  }
}
