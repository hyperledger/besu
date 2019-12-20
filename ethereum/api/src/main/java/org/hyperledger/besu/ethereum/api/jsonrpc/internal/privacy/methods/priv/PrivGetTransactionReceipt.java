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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.enclave.EnclaveClientException;
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
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivGetTransactionReceipt implements JsonRpcMethod {

  private static final Logger LOG = getLogger();

  private final BlockchainQueries blockchain;
  private PrivacyParameters privacyParameters;
  private PrivacyController privacyController;

  public PrivGetTransactionReceipt(
      final BlockchainQueries blockchain,
      final PrivacyParameters privacyParameters,
      final PrivacyController privacyController) {
    this.blockchain = blockchain;
    this.privacyParameters = privacyParameters;
    this.privacyController = privacyController;
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

    final PrivateTransaction privateTransaction;
    final String privacyGroupId;
    try {
      final ReceiveResponse receiveResponse =
          privacyController.retrieveTransaction(transaction.getPayload().toBase64String());
      LOG.trace("Received transaction information");

      final BytesValueRLPInput input =
          new BytesValueRLPInput(
              Bytes.fromBase64String(new String(receiveResponse.getPayload(), UTF_8)), false);

      privateTransaction = PrivateTransaction.readFrom(input);
      privacyGroupId = receiveResponse.getPrivacyGroupId();
    } catch (final EnclaveClientException e) {
      return handleEnclaveException(requestContext, e);
    }

    final String contractAddress =
        !privateTransaction.getTo().isPresent()
            ? Address.privateContractAddress(
                    privateTransaction.getSender(),
                    privateTransaction.getNonce(),
                    Bytes.fromBase64String(privacyGroupId))
                .toString()
            : null;

    LOG.trace("Calculated contractAddress: {}", contractAddress);

    final Bytes rlpEncoded = RLP.encode(privateTransaction::writeTo);
    final Bytes32 txHash = org.hyperledger.besu.crypto.Hash.keccak256(rlpEncoded);

    LOG.trace("Calculated private transaction hash: {}", txHash);

    final List<Log> transactionLogs =
        privacyParameters
            .getPrivateStateStorage()
            .getTransactionLogs(txHash)
            .orElse(Collections.emptyList());

    LOG.trace("Processed private transaction events");

    final Bytes transactionOutput =
        privacyParameters.getPrivateStateStorage().getTransactionOutput(txHash).orElse(Bytes.EMPTY);

    final Bytes revertReason =
        privacyParameters.getPrivateStateStorage().getRevertReason(txHash).orElse(null);

    final String transactionStatus =
        Quantity.create(
            privacyParameters
                .getPrivateStateStorage()
                .getStatus(txHash)
                .orElse(Bytes.EMPTY)
                .toUnsignedBigInteger());

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

  private JsonRpcResponse handleEnclaveException(
      final JsonRpcRequestContext requestContext, final EnclaveClientException e) {
    final JsonRpcError jsonRpcError =
        JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason(e.getMessage());
    switch (jsonRpcError) {
      case ENCLAVE_PAYLOAD_NOT_FOUND:
        {
          return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
        }
      case ENCLAVE_KEYS_CANNOT_DECRYPT_PAYLOAD:
        {
          LOG.warn(
              "Unable to decrypt payload with configured privacy node key. Check if your 'privacy-public-key-file' property matches your Orion node public key.");
        }
        // fall through
      default:
        throw e;
    }
  }
}
