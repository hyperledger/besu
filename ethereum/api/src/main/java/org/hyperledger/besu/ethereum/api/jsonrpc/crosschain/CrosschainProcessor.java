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
package org.hyperledger.besu.ethereum.api.jsonrpc.crosschain;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.crosschain.CrosschainConfiguration;
import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.crosschain.CrosschainThreadLocalDataHolder;
import org.hyperledger.besu.ethereum.crosschain.SubordinateViewCoordinator;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrosschainProcessor {
  protected static final Logger LOG = LogManager.getLogger();

  SubordinateViewCoordinator subordinateViewCoordinator;
  TransactionSimulator transactionSimulator;
  TransactionPool transactionPool;

  public CrosschainProcessor(
      final SubordinateViewCoordinator subordinateViewCoordinator,
      final TransactionSimulator transactionSimulator,
      final TransactionPool transactionPool) {
    this.subordinateViewCoordinator = subordinateViewCoordinator;
    this.transactionSimulator = transactionSimulator;
    this.transactionPool = transactionPool;
  }

  /**
   * Execute a subordinate transaction.
   *
   * @param transaction Subordinate Transaction to execute.
   * @return Validaiton result.
   */
  public ValidationResult<TransactionValidator.TransactionInvalidReason> addLocalTransaction(
      final CrosschainTransaction transaction) {
    // Get Subordinate View results.
    if (processSubordinates(transaction, false)) {
      return ValidationResult.invalid(
          TransactionValidator.TransactionInvalidReason.FAILED_SUBORDINATE_VIEW);
    }

    Optional<ValidationResult<TransactionValidator.TransactionInvalidReason>> executionError =
        trialExecution(transaction);
    if (executionError.isPresent()) {
      return executionError.get();
    }

    // Dispatch Subordinate Transactions if the trial execution worked OK.
    if (processSubordinates(transaction, true)) {
      return ValidationResult.invalid(
          TransactionValidator.TransactionInvalidReason.FAILED_SUBORDINATE_VIEW);
    }

    return this.transactionPool.addLocalTransaction(transaction);
  }

  /**
   * Execute a subordinate view.
   *
   * @param transaction The subordinate view to process.
   * @param blockNumber Execute view at this block number.
   * @return Result or an error.
   */
  public Object getSignedResult(final CrosschainTransaction transaction, final long blockNumber) {
    // Get Subordinate View results.
    if (processSubordinates(transaction, false)) {
      return TransactionValidator.TransactionInvalidReason.FAILED_SUBORDINATE_VIEW;
    }
    return this.subordinateViewCoordinator.getSignedResult(transaction, blockNumber);
  }

  private boolean processSubordinates(
      final CrosschainTransaction transaction, final boolean processSubbordianteTransactions) {
    for (CrosschainTransaction subordinateTransactionsAndView :
        transaction.getSubordinateTransactionsAndViews()) {
      if ((processSubbordianteTransactions
              && subordinateTransactionsAndView.getType().isSubordinateTransaction())
          || (!processSubbordianteTransactions
              && subordinateTransactionsAndView.getType().isSubordinateView())) {

        String method =
            subordinateTransactionsAndView.getType().isSubordinateView()
                ? "eth_processSubordinateView"
                : "eth_sendRawCrosschainTransaction";

        BytesValueRLPOutput out = new BytesValueRLPOutput();
        subordinateTransactionsAndView.writeTo(out);
        BytesValue signedTransaction = out.encoded();

        if (signedTransaction == null) {
          LOG.error("Unexpectedly, subordinate view is null");
          // Indicate execution failed unexpectedly.
          return true;
        }

        Optional<BigInteger> optionalSidechainId = subordinateTransactionsAndView.getChainId();
        BigInteger sidechainId = optionalSidechainId.orElse(BigInteger.ZERO);
        // TODO Allow for BigInteger chainids.
        int chainId = sidechainId.intValue();

        // Get the address from chain mapping.
        String ipAddress = CrosschainConfiguration.chainsMapping.get(chainId);
        String response = null;
        LOG.info("Send cross chain transaction or view to chain at " + ipAddress);
        try {
          response = post(ipAddress, method, signedTransaction.toString());
          LOG.info("Crosschain Response: " + response);
        } catch (Exception e) {
          LOG.error("Exception during crosschain happens here: " + e.getMessage());
          // Indicate execution failed unexpectedly.
          return true;
        }

        BytesValue result = processResult(response);

        // TODO If this is a subordinate view
        // TODO verify the signature of the result
        // TODO check that the Subordiante View hash returned matches the submitted subordiante
        // view.
        LOG.info("Crosschain Result: " + result.toString());
        subordinateTransactionsAndView.addSignedResult(result);
      }
    }

    return false;
  }

  /**
   * Do a trial execution of the Crosschain Transaction.
   *
   * @param subordinateTransaction transaction to execute.
   * @return Empty if the transaction and subordinate views execute correctly, otherwise an error is
   *     returned.
   */
  public Optional<ValidationResult<TransactionValidator.TransactionInvalidReason>> trialExecution(
      final CrosschainTransaction subordinateTransaction) {
    // Add to thread local storage.
    CrosschainThreadLocalDataHolder.setCrosschainTransaciton(subordinateTransaction);
    // Rewind to the first subordinate transaction or view for each execution.
    subordinateTransaction.resetSubordinateTransactionsAndViewsList();

    Optional<TransactionSimulatorResult> result =
        this.transactionSimulator.processAtHead(subordinateTransaction);
    CrosschainThreadLocalDataHolder.removeCrosschainTransaction();

    if (result.isPresent()) {
      TransactionSimulatorResult simulatorResult = result.get();
      LOG.info("Transaction Simulation Result {}", simulatorResult.getResult().getStatus());

      if (simulatorResult.isSuccessful()) {
        return Optional.empty();
      }
      // The transaction may have failed, but the transaction is valid. This could occur when a
      // revert is thrown
      // while executing the code.
      if (simulatorResult.getValidationResult().isValid()) {
        // TODO If we return a TransactionInvalidReason, then the HTTP response will be 400.
        // Hence, return as if everything has been successful, and rely on the user to see that no
        // status update occurred as a result of their transaction.
        return Optional.empty();
      }
      return Optional.of(simulatorResult.getValidationResult());
    }
    return Optional.of(
        ValidationResult.invalid(TransactionValidator.TransactionInvalidReason.UNKNOWN_FAILURE));
  }

  private static String post(final String address, final String method, final String params)
      throws Exception {
    URL url = new URL("http://" + address);
    URLConnection con = url.openConnection();
    HttpURLConnection http = (HttpURLConnection) con;
    http.setRequestMethod("POST");
    http.setDoOutput(true);
    byte[] out =
        ("{\"jsonrpc\":\"2.0\",\"method\":\""
                + method
                + "\",\"params\":[\""
                + params
                + "\"],\"id\":1}")
            .getBytes(UTF_8);
    int length = out.length;
    http.setFixedLengthStreamingMode(length);
    http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    http.connect();
    OutputStream os = http.getOutputStream();
    os.write(out);
    BufferedReader in = new BufferedReader(new InputStreamReader(http.getInputStream(), UTF_8));
    String line;
    String response = "";
    while ((line = in.readLine()) != null) {
      response += line;
    }
    os.close();
    in.close();
    http.disconnect();
    return response;
  }

  private BytesValue processResult(final String response) {
    final JsonObject responseJson = new JsonObject(response);
    String result = responseJson.getString("result");
    return BytesValue.fromHexString(result);
  }
}
