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
package org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods;

import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;

import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.CrosschainProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.util.bytes.BytesValue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Process a Crosschain Subordinate View. */
public class EthProcessSubordinateView implements JsonRpcMethod {

  private static final Logger LOG = LogManager.getLogger();

  private final JsonRpcParameter parameters;

  private final BlockchainQueries blockchain;

  private final CrosschainProcessor crosschainProcessor;

  public EthProcessSubordinateView(
      final BlockchainQueries blockchain,
      final CrosschainProcessor crosschainProcessor,
      final JsonRpcParameter parameters) {
    this.parameters = parameters;
    this.blockchain = blockchain;
    this.crosschainProcessor = crosschainProcessor;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_PROCESS_RAW_SUBORDINATE_VIEW.getMethodName();
  }

  @Override
  // @SuppressWarnings("ModifiedButNotUsed")
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 1) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
    final String rawTransaction = parameters.required(request.getParams(), 0, String.class);

    final CrosschainTransaction transaction;
    try {
      transaction = decodeRawCrosschainTransaction(rawTransaction);
    } catch (final InvalidJsonRpcRequestException e) {
      LOG.error(e);
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }

    LOG.info(prettyPrintJSON(transaction.toString()));

    // Check that the transaction is a SubordinateView, and that all of the contained
    // Subordinate Transactions and Views are only Views.
    if (!transaction.getType().isSubordinateView()) {
      LOG.error(getName() + " called with a non-Subordinate View");
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
    if (foundNonSubordinateViewsInHierarchy(transaction)) {
      LOG.error(getName() + " called with an nested non-Subordinate View");
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }

    // Do the Subordinate View based on the current block number.
    long blockNumber = blockchain.headBlockNumber();

    // TODO this will block for a long time while the result is being calculated.
    //   Given we are not using inter-node communication for the threshold signature
    //   calculation we won't notice this problem.
    // Maybe we should return a hash, and the result can be fetched later based on the hash?

    Object resultOrError = this.crosschainProcessor.getSignedResult(transaction, blockNumber);
    if (resultOrError == null) {
      LOG.info("Transaction Simulation returned null as error");
      throw new Error("Unexpected result: null");
    }
    if (resultOrError instanceof TransactionSimulatorResult) {
      LOG.info("Simulator Result: " + ((TransactionSimulatorResult) resultOrError).getOutput());

      return new JsonRpcSuccessResponse(
          request.getId(), ((TransactionSimulatorResult) resultOrError).getOutput().toString());
    } else if (resultOrError instanceof TransactionValidator.TransactionInvalidReason) {
      JsonRpcError error =
          convertTransactionInvalidReason(
              (TransactionValidator.TransactionInvalidReason) resultOrError);
      LOG.info("Transaction Validator Invalid Reason: {}, {}", error.getCode(), error.getMessage());
      return new JsonRpcErrorResponse(
          request.getId(),
          convertTransactionInvalidReason(
              (TransactionValidator.TransactionInvalidReason) resultOrError));
    } else {
      LOG.info("Transaction Simulation returned an unknown error: {}", resultOrError);
      throw new Error("Transaction Simulation returned an unknown error");
    }
  }

  private String prettyPrintJSON(final String raw) {
    String result = "\n";
    int num = 0;
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '{' || c == '[') {
        num++;
      } else if (c == '}' || c == ']') {
        num--;
      }
      if (c == ' ') continue;
      result += c;
      if (c == '{' || c == '}' || c == ',' || c == '[' || c == ']') {
        result += '\n';
        for (int j = 0; j < num; j++) result += '\t';
      }
    }
    return result;
  }

  private CrosschainTransaction decodeRawCrosschainTransaction(final String hash)
      throws InvalidJsonRpcRequestException {
    try {
      // TODO we should do some check to ensure this is a Subordinate View, and that this
      // TODO    Subordinate View only contains other Subordinate View
      return CrosschainTransaction.readFrom(RLP.input(BytesValue.fromHexString(hash)));
    } catch (final IllegalArgumentException | RLPException e) {
      LOG.error(e);
      throw new InvalidJsonRpcRequestException("Invalid raw transaction hex", e);
    }
  }

  /**
   * Check that all "transactions" in the hierarchy are Subordinate Views.
   *
   * @param transaction Transaction / view to start searching from.
   * @return false if all "transactions" below this point are Subordinate Views.
   */
  private boolean foundNonSubordinateViewsInHierarchy(final CrosschainTransaction transaction) {
    transaction.resetSubordinateTransactionsAndViewsList();
    CrosschainTransaction subordinateTx;
    while ((subordinateTx = transaction.getNextSubordinateTransactionOrView()) != null) {
      if (!subordinateTx.getType().isSubordinateView()) {
        LOG.error(getName() + " called with a non-Subordinate View");
        return true;
      }
      // Check any subordiante views contained within this subordiante view.
      if (foundNonSubordinateViewsInHierarchy(subordinateTx)) {
        return true;
      }
    }
    return false;
  }
}
