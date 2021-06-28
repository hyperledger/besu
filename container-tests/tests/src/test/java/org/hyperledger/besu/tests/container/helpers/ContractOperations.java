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
package org.hyperledger.besu.tests.container.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.web3j.abi.FunctionEncoder;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.quorum.Quorum;
import org.web3j.quorum.tx.QuorumTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

public class ContractOperations {

  public static final String SIMPLE_STORAGE_CONTRACT_BYTECODE =
      "6080604052348015600f57600080fd5b5060de8061001e6000396000f3fe6080604052348015600f57600080fd5b506004361060275760003560e01c8062f88abf14602c575b600080fd5b605560048036036020811015604057600080fd5b81019080803590602001909291905050506057565b005b3373ffffffffffffffffffffffffffffffffffffffff167f748aa07c80b05bd067e3688dbb79d9f9583cd018be6a589a7c364cacd770e0a2826040518082815260200191505060405180910390a25056fea26469706673582212207df0d3ad8bced04b7bd476cc81a6233c0b575966c29b4af96450313628ee623964736f6c63430007040033";
  public static String SIMPLE_STORAGE_CONTRACT_WITH_CONSTRUCTOR;

  public static String deployContractAndReturnAddress(
      final Quorum quorumWeb3j,
      final Credentials credentials,
      final QuorumTransactionManager qtm,
      final PollingTransactionReceiptProcessor pollingTransactionReceiptProcessorReturn,
      final PollingTransactionReceiptProcessor pollingTransactionReceiptProcessorIgnore)
      throws IOException, TransactionException {
    // Build smart contract transaction
    final String encodedConstructor = FunctionEncoder.encodeConstructor(Collections.emptyList());
    SIMPLE_STORAGE_CONTRACT_WITH_CONSTRUCTOR =
        SIMPLE_STORAGE_CONTRACT_BYTECODE + encodedConstructor;

    final RawTransaction contractTransaction =
        RawTransaction.createTransaction(
            BigInteger.valueOf(getNonce(quorumWeb3j, credentials)),
            BigInteger.ZERO,
            BigInteger.valueOf(4300000),
            "",
            BigInteger.ZERO,
            SIMPLE_STORAGE_CONTRACT_WITH_CONSTRUCTOR);

    // Send the signed transaction to quorum
    final EthSendTransaction sendContractTransactionResult = qtm.signAndSend(contractTransaction);
    assertThat(sendContractTransactionResult.hasError()).isFalse();

    pollNodeForTransactionReceipt(
        pollingTransactionReceiptProcessorIgnore,
        sendContractTransactionResult.getTransactionHash());

    // Poll for the transaction receipt
    final TransactionReceipt transactionReceiptResult =
        pollNodeForTransactionReceiptResult(
            pollingTransactionReceiptProcessorReturn,
            sendContractTransactionResult.getTransactionHash());

    return transactionReceiptResult.getContractAddress();
  }

  public static String getCode(final Quorum web3j, final String contractAddress)
      throws IOException {
    final DefaultBlockParameter blockParam =
        DefaultBlockParameter.valueOf(DefaultBlockParameterName.LATEST.toString());
    final EthGetCode codeResult = web3j.ethGetCode(contractAddress, blockParam).send();
    assertThat(codeResult.getCode())
        .withFailMessage("Code for contractAddress not found.")
        .isNotEmpty();
    return codeResult.getCode();
  }

  public static String getTransactionLog(final Quorum web3j, final String transactionHash)
      throws IOException {
    final EthGetTransactionReceipt transactionReceiptResult =
        web3j.ethGetTransactionReceipt(transactionHash).send();
    assertThat(transactionReceiptResult.getTransactionReceipt())
        .withFailMessage("Transaction for log not found.")
        .isNotEmpty();
    final List<Log> logs = transactionReceiptResult.getTransactionReceipt().get().getLogs();
    assertThat(logs.size()).withFailMessage("No log found.").isEqualTo(1);
    // Remove the 0x prefix
    return logs.get(0).getData().substring(2);
  }

  public static String sendLogEventAndReturnTransactionHash(
      final Quorum quorum,
      final Credentials credentials,
      final String contractAddress,
      final QuorumTransactionManager qtm,
      final PollingTransactionReceiptProcessor pollingTransactionReceiptProcessorReturn,
      final PollingTransactionReceiptProcessor pollingTransactionReceiptProcessorIgnore,
      final String param)
      throws IOException, TransactionException {
    final String functionSig = "0x00f88abf";
    final String data = functionSig + param;
    final RawTransaction logEventTransaction =
        RawTransaction.createTransaction(
            BigInteger.valueOf(getNonce(quorum, credentials)),
            BigInteger.ZERO,
            BigInteger.valueOf(4300000),
            contractAddress,
            BigInteger.ZERO,
            data);

    final EthSendTransaction sendTransactionResult = qtm.signAndSend(logEventTransaction);
    assertThat(sendTransactionResult.hasError()).isFalse();

    pollNodeForTransactionReceipt(
        pollingTransactionReceiptProcessorIgnore, sendTransactionResult.getTransactionHash());

    final TransactionReceipt logReceiptResult =
        pollNodeForTransactionReceiptResult(
            pollingTransactionReceiptProcessorReturn, sendTransactionResult.getTransactionHash());

    return logReceiptResult.getTransactionHash();
  }

  public static String generate64BytesHexString(final long number) {
    final String str = Long.toHexString(number);

    return prependZeroesToPadHexStringToGivenLength(str, 64);
  }

  @NotNull
  public static String prependZeroesToPadHexStringToGivenLength(
      final String hexString, final int lenRequested) {
    final StringBuilder sb = new StringBuilder(hexString);
    while (sb.length() < lenRequested) {
      sb.insert(0, '0');
    }
    return sb.toString();
  }

  public static int getNonce(final Quorum quorum, final Credentials credentials)
      throws IOException {
    final EthGetTransactionCount transactionCountResult =
        quorum
            .ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST)
            .send();
    assertThat(transactionCountResult.hasError()).isFalse();
    return transactionCountResult.getTransactionCount().intValueExact();
  }

  public static TransactionReceipt pollNodeForTransactionReceiptResult(
      final PollingTransactionReceiptProcessor pollingTransactionReceiptProcessor,
      final String transactionHash)
      throws IOException, TransactionException {
    final TransactionReceipt transactionReceiptResult =
        pollingTransactionReceiptProcessor.waitForTransactionReceipt(transactionHash);
    assertThat(transactionReceiptResult.isStatusOK()).isTrue();
    return transactionReceiptResult;
  }

  public static void pollNodeForTransactionReceipt(
      final PollingTransactionReceiptProcessor pollingTransactionReceiptProcessor,
      final String transactionHash)
      throws IOException, TransactionException {
    final TransactionReceipt transactionReceiptResult =
        pollingTransactionReceiptProcessor.waitForTransactionReceipt(transactionHash);
    assertThat(transactionReceiptResult.isStatusOK()).isTrue();
  }
}
