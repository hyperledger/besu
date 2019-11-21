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
package org.hyperledger.besu.crosschain.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.config.CrosschainConfigOptions;
import org.hyperledger.besu.crosschain.ethereum.crosschain.CrosschainThreadLocalDataHolder;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrosschainProcessor {
  protected static final Logger LOG = LogManager.getLogger();

  TransactionSimulator transactionSimulator;
  TransactionPool transactionPool;
  SECP256K1.KeyPair nodeKeys;
  Blockchain blockchain;
  WorldStateArchive worldStateArchive;
  BigInteger sidechainId;

  Vertx vertx;

  public void init(
      final TransactionSimulator transactionSimulator,
      final TransactionPool transactionPool,
      final BigInteger sidechainId,
      final SECP256K1.KeyPair nodeKeys,
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive) {
    this.transactionSimulator = transactionSimulator;
    this.transactionPool = transactionPool;
    this.sidechainId = sidechainId;
    this.nodeKeys = nodeKeys;
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;

    this.vertx = Vertx.vertx();
  }

  /**
   * Process subordinate transactions or subordinate views.
   *
   * @param transaction The Originating Transaction, Subordinate Transaction or Subordinate View to
   *     fetch the subordinate Subordinate Transactions or Views from.
   * @param processSubordinateTransactions true if transactions rather than views should be
   *     processed.
   * @return true if execution failed.
   */
  boolean processSubordinates(
      final CrosschainTransaction transaction, final boolean processSubordinateTransactions) {
    List<CrosschainTransaction> subordinates = transaction.getSubordinateTransactionsAndViews();
    for (CrosschainTransaction subordinateTransactionsAndView : subordinates) {
      if ((processSubordinateTransactions
              && subordinateTransactionsAndView.getType().isSubordinateTransaction())
          || (!processSubordinateTransactions
              && subordinateTransactionsAndView.getType().isSubordinateView())) {

        String method =
            subordinateTransactionsAndView.getType().isSubordinateView()
                ? "eth_processSubordinateView"
                : "eth_sendRawCrosschainTransaction";

        BytesValueRLPOutput out = new BytesValueRLPOutput();
        subordinateTransactionsAndView.writeTo(out);
        BytesValue signedTransaction = out.encoded();

        if (signedTransaction == null) {
          LOG.error("Subordinate view does not exist");
          // Indicate execution failed unexpectedly.
          return true;
        }

        Optional<BigInteger> optionalSidechainId = subordinateTransactionsAndView.getChainId();
        BigInteger sidechainId = optionalSidechainId.orElse(BigInteger.ZERO);
        // TODO Allow for BigInteger chainids.
        int chainId = sidechainId.intValue();

        // Get the address from chain mapping.
        String ipAddress = CrosschainConfigOptions.chainsMapping.get(chainId);
        String response = null;
        LOG.debug("Sending Crosschain Transaction or view to chain at " + ipAddress);
        try {
          response = post(ipAddress, method, signedTransaction.toString());
          LOG.debug("Crosschain Response: " + response);
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
  Optional<ValidationResult<TransactionValidator.TransactionInvalidReason>> trialExecution(
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
      LOG.info("Transaction Simulation Status {}", simulatorResult.getResult().getStatus());

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
        return Optional.of(
            ValidationResult.invalid(
                TransactionValidator.TransactionInvalidReason.CROSSCHAIN_FAILED_EXECUTION));
      }
      return Optional.of(simulatorResult.getValidationResult());
    }
    return Optional.of(
        ValidationResult.invalid(
            TransactionValidator.TransactionInvalidReason.CROSSCHAIN_UNKNOWN_FAILURE));
  }

  /**
   * TODO THIS METHOD SHOULD PROBABLY MERGE WITH THE ONE ABOVE Process the Subordinate View for this
   * block number.
   *
   * @param subordinateView Subordinate view to execute.
   * @param blockNumber block number to execute the view call at.
   * @return TransactionSimulatorResult if the execution completed. TransactionInvalidReason if
   *     there was an error.
   */
  public Object executeSubordinateView(
      final CrosschainTransaction subordinateView, final long blockNumber) {
    return this.transactionSimulator
        .process(subordinateView, blockNumber)
        .map(result -> result.getValidationResult().either((() -> result), reason -> reason))
        .orElse(null);
  }

  // TODO this should be implemented as a Vertx HTTPS Client. We should probably submit all
  // TODO Subordinate Views together, and wait for them to all return, and submit all
  //  Subordinate Transactions together and wait for them to all return.
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

  void startCrosschainTransactionCommitIgnoreTimeOut(final CrosschainTransaction transaction) {
    this.vertx.setTimer(
        2000,
        id -> {
          // Work out TO address.
          Address toAddress =
              transaction.getTo().orElse(transaction.contractAddress().orElse(Address.ZERO));
          sendSignallingTransaction(toAddress);
        });
  }

  /**
   * Send a signalling transaction to an address to unlock a contract.
   *
   * <p>TODO we should probably check the response and retry if appropriate.
   *
   * @param toAddress Address of contract to unlock / send the signalling transaction to.
   */
  void sendSignallingTransaction(final Address toAddress) {
    LOG.debug("Crosschain Signalling Transaction: Initiated");

    // Work out sender's nonce.
    // TODO The code below only determines the nonce up until the latest block. It does not
    // TODO look at pending transactions.
    Hash latestBlockStateRootHash = this.blockchain.getChainHeadBlock().getHeader().getStateRoot();
    final Optional<MutableWorldState> maybeWorldState =
        worldStateArchive.getMutable(latestBlockStateRootHash);
    if (maybeWorldState.isEmpty()) {
      LOG.error("Crosschain Signalling Transaction: Can't fetch world state");
      return;
    }
    MutableWorldState worldState = maybeWorldState.get();
    final Address senderAddress =
        Address.extract(Hash.hash(this.nodeKeys.getPublicKey().getEncodedBytes()));
    final Account sender = worldState.get(senderAddress);
    final long nonce = sender != null ? sender.getNonce() : 0L;

    if (toAddress.equals(Address.ZERO)) {
      LOG.error("Crosschain Signalling Transaction: No TO address specified");
      return;
    }

    List<CrosschainTransaction> emptyList = List.of();

    CrosschainTransaction ignoreCommitTransaction =
        CrosschainTransaction.builderX()
            .type(
                CrosschainTransaction.CrosschainTransactionType
                    .UNLOCK_COMMIT_SIGNALLING_TRANSACTION)
            .nonce(nonce)
            .gasPrice(Wei.ZERO)
            .gasLimit(10000000)
            .to(toAddress)
            .value(Wei.ZERO)
            .payload(BytesValue.EMPTY)
            .chainId(this.sidechainId)
            .subordinateTransactionsAndViews(emptyList)
            .signAndBuild(this.nodeKeys);

    ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult =
        this.transactionPool.addLocalTransaction(ignoreCommitTransaction);
    if (!validationResult.isValid()) {
      LOG.warn(
          "Crosschain Signalling Transaction: Validation result:{}", validationResult.toString());
    }
  }
}
