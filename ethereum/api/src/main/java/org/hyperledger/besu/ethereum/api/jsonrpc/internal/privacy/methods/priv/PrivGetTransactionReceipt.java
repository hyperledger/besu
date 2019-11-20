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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveException;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionReceiptResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
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

public class PrivGetTransactionReceipt implements JsonRpcMethod {

  private static final Logger LOG = getLogger();

  private final BlockchainQueries blockchain;
  private final Enclave enclave;
  private final PrivacyParameters privacyParameters;

  public PrivGetTransactionReceipt(
      final BlockchainQueries blockchain,
      final Enclave enclave,
      final PrivacyParameters privacyParameters) {
    this.blockchain = blockchain;
    this.enclave = enclave;
    this.privacyParameters = privacyParameters;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_TRANSACTION_RECEIPT.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    LOG.trace("Executing {}", RpcMethod.PRIV_GET_TRANSACTION_RECEIPT.getMethodName());
    final Hash transactionHash = requestContext.getRequiredParameter(0, Hash.class);
    final Optional<TransactionLocation> maybeLocation =
        blockchain.getBlockchain().getTransactionLocation(transactionHash);
    if (!maybeLocation.isPresent()) {
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
    }
    final TransactionLocation location = maybeLocation.get();
    final BlockBody blockBody =
        blockchain.getBlockchain().getBlockBody(location.getBlockHash()).get();
    final Transaction transaction = blockBody.getTransactions().get(location.getTransactionIndex());

    final Hash blockhash = location.getBlockHash();
    final long blockNumber = blockchain.getBlockchain().getBlockHeader(blockhash).get().getNumber();

    final String publicKey = privacyParameters.getEnclavePublicKey();
    final PrivateTransaction privateTransaction;
    final String privacyGroupId;
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
        return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
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

    final BytesValue rlpEncoded = RLP.encode(privateTransaction::writeTo);
    final Bytes32 txHash = org.hyperledger.besu.crypto.Hash.keccak256(rlpEncoded);

    LOG.trace("Calculated private transaction hash: {}", txHash);

    final List<Log> transactionLogs =
        privacyParameters
            .getPrivateStateStorage()
            .getTransactionLogs(txHash)
            .orElse(Collections.emptyList());

    LOG.trace("Processed private transaction events");

    final BytesValue transactionOutput =
        privacyParameters
            .getPrivateStateStorage()
            .getTransactionOutput(txHash)
            .orElse(BytesValue.wrap(new byte[0]));

    final BytesValue revertReason =
        privacyParameters.getPrivateStateStorage().getRevertReason(txHash).orElse(null);

    final String transactionStatus =
        Quantity.create(
            BytesValues.asUnsignedBigInteger(
                privacyParameters
                    .getPrivateStateStorage()
                    .getStatus(txHash)
                    .orElse(BytesValue.wrap(new byte[0]))));

    LOG.trace("Processed private transaction output");

    final PrivateTransactionReceiptResult result =
        new PrivateTransactionReceiptResult(
            contractAddress,
            privateTransaction.getSender().toString(),
            privateTransaction.getTo().map(Address::toString).orElse(null),
            transactionLogs,
            transactionOutput,
            blockhash,
            blockNumber,
            location.getTransactionIndex(),
            transaction.getHash(),
            privateTransaction.hash(),
            privateTransaction.getPrivateFrom(),
            privateTransaction.getPrivateFor().orElse(null),
            privateTransaction.getPrivacyGroupId().orElse(null),
            revertReason,
            transactionStatus);

    LOG.trace("Created Private Transaction from given Transaction Hash");

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
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
