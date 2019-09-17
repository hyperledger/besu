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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.crosschain.CrosschainProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.util.bytes.BytesValue;

import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;


/**
 * Process either a Crosschain Originating Transaction or a Subordinate Transaction.
 */
public class EthSendRawCrosschainTransaction implements JsonRpcMethod {

  private static final Logger LOG = LogManager.getLogger();

  private final CrosschainProcessor crosschainProcessor;
  private final JsonRpcParameter parameters;

  public EthSendRawCrosschainTransaction(
          final CrosschainProcessor crosschainProcessor, final JsonRpcParameter parameters) {
    this.crosschainProcessor = crosschainProcessor;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_SEND_RAW_CROSSCHAIN_TRANSACTION.getMethodName();
  }

  @Override
  //@SuppressWarnings("ModifiedButNotUsed")
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

    final ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult =
        this.crosschainProcessor.addLocalTransaction(transaction);

    return validationResult.either(
        () -> new JsonRpcSuccessResponse(request.getId(), transaction.hash().toString()),
        errorReason ->
            new JsonRpcErrorResponse(
                request.getId(), convertTransactionInvalidReason(errorReason)));
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
      return CrosschainTransaction.readFrom(RLP.input(BytesValue.fromHexString(hash)));
    } catch (final IllegalArgumentException | RLPException e) {
      LOG.error(e);
      throw new InvalidJsonRpcRequestException("Invalid raw transaction hex", e);
    }
  }
}
