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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveException;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionReceiptResult;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.Logger;

public class EeaGetTransactionReceipt implements JsonRpcMethod {

  private static final Logger LOG = getLogger();

  private final BlockchainQueries blockchain;
  private final Enclave enclave;
  private final JsonRpcParameter parameters;
  private final PrivacyParameters privacyParameters;

  public EeaGetTransactionReceipt(
      final BlockchainQueries blockchain,
      final Enclave enclave,
      final JsonRpcParameter parameters,
      final PrivacyParameters privacyParameters) {
    this.blockchain = blockchain;
    this.enclave = enclave;
    this.parameters = parameters;
    this.privacyParameters = privacyParameters;
  }

  @Override
  public String getName() {
    return RpcMethod.EEA_GET_TRANSACTION_RECEIPT.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    LOG.trace("Executing {}", RpcMethod.EEA_GET_TRANSACTION_RECEIPT.getMethodName());
    final Hash transactionHash = parameters.required(request.getParams(), 0, Hash.class);
    final Optional<TransactionLocation> maybeLocation =
        blockchain.getBlockchain().getTransactionLocation(transactionHash);
    if (!maybeLocation.isPresent()) {
      return new JsonRpcSuccessResponse(request.getId(), null);
    }
    final TransactionLocation location = maybeLocation.get();
    final BlockBody blockBody =
        blockchain.getBlockchain().getBlockBody(location.getBlockHash()).get();
    final Transaction transaction = blockBody.getTransactions().get(location.getTransactionIndex());

    final Hash blockhash = location.getBlockHash();
    final long blockNumber = blockchain.getBlockchain().getBlockHeader(blockhash).get().getNumber();

    final String publicKey = privacyParameters.getEnclavePublicKey();
    PrivateTransaction privateTransaction;
    String privacyGroupId;
    try {
      final ReceiveResponse receiveResponse = getReceiveResponseFromEnclave(transaction, publicKey);
      LOG.trace("Received transaction information from Enclave");

      final BytesValueRLPInput bytesValueRLPInput =
          new BytesValueRLPInput(BytesValues.fromBase64(receiveResponse.getPayload()), false);

      privateTransaction = PrivateTransaction.readFrom(bytesValueRLPInput);
      privacyGroupId = receiveResponse.getPrivacyGroupId();
    } catch (final EnclaveException e) {
      if (JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason(e.getMessage())
          == JsonRpcError.ENCLAVE_PAYLOAD_NOT_FOUND) {
        return new JsonRpcSuccessResponse(request.getId(), null);
      }
      throw e;
    }

    final String contractAddress =
        !privateTransaction.getTo().isPresent()
            ? Address.privateContractAddress(
                    privateTransaction.getSender(),
                    privateTransaction.getNonce(),
                    BytesValues.fromBase64(privacyGroupId))
                .toString()
            : null;

    LOG.trace("Calculated contractAddress: {}", contractAddress);

    BytesValue rlpEncoded = RLP.encode(privateTransaction::writeTo);
    Bytes32 txHash = org.hyperledger.besu.crypto.Hash.keccak256(rlpEncoded);

    LOG.trace("Calculated private transaction hash: {}", txHash);

    List<Log> events =
        privacyParameters
            .getPrivateTransactionStorage()
            .getEvents(txHash)
            .orElse(Collections.emptyList());

    LOG.trace("Processed private transaction events");

    BytesValue output =
        privacyParameters
            .getPrivateTransactionStorage()
            .getOutput(txHash)
            .orElse(BytesValue.wrap(new byte[0]));

    LOG.trace("Processed private transaction output");

    PrivateTransactionReceiptResult result =
        new PrivateTransactionReceiptResult(
            contractAddress,
            privateTransaction.getSender().toString(),
            privateTransaction.getTo().map(Address::toString).orElse(null),
            events,
            output,
            blockhash,
            transactionHash,
            blockNumber,
            location.getTransactionIndex());

    LOG.trace("Created Private Transaction from given Transaction Hash");

    return new JsonRpcSuccessResponse(request.getId(), result);
  }

  private ReceiveResponse getReceiveResponseFromEnclave(
      final Transaction transaction, final String publicKey) {
    LOG.trace("Fetching transaction information from Enclave");
    final ReceiveRequest enclaveRequest =
        new ReceiveRequest(BytesValues.asBase64String(transaction.getPayload()), publicKey);
    final ReceiveResponse enclaveResponse = enclave.receive(enclaveRequest);
    LOG.trace("Received transaction information from Enclave");
    return enclaveResponse;
  }
}
