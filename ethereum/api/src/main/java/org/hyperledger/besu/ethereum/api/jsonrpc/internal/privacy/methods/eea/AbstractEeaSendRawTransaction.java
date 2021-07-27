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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.DECODE_ERROR;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.util.NonceProvider;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;

import java.util.Optional;
import java.util.concurrent.locks.Lock;

import com.google.common.util.concurrent.Striped;
import io.vertx.ext.auth.User;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public abstract class AbstractEeaSendRawTransaction implements JsonRpcMethod {
  private static final Logger LOG = getLogger();
  private final TransactionPool transactionPool;
  private final PrivacyIdProvider privacyIdProvider;
  private final PrivateMarkerTransactionFactory privateMarkerTransactionFactory;
  private final BlockchainQueries blockchainQueries;
  private final NonceProvider publicNonceProvider;
  private final GasCalculator gasCalculator;
  private static final int MAX_CONCURRENT_PMT_SIGNATURE_REQUESTS = 10;
  private final Striped<Lock> stripedLock =
      Striped.lazyWeakLock(MAX_CONCURRENT_PMT_SIGNATURE_REQUESTS);

  protected AbstractEeaSendRawTransaction(
      final TransactionPool transactionPool,
      final PrivacyIdProvider privacyIdProvider,
      final PrivateMarkerTransactionFactory privateMarkerTransactionFactory,
      final BlockchainQueries blockchainQueries,
      final NonceProvider publicNonceProvider,
      final GasCalculator gasCalculator) {
    this.transactionPool = transactionPool;
    this.privacyIdProvider = privacyIdProvider;
    this.privateMarkerTransactionFactory = privateMarkerTransactionFactory;
    this.blockchainQueries = blockchainQueries;
    this.publicNonceProvider = publicNonceProvider;
    this.gasCalculator = gasCalculator;
  }

  @Override
  public String getName() {
    return RpcMethod.EEA_SEND_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Object id = requestContext.getRequest().getId();
    final Optional<User> user = requestContext.getUser();
    final String rawPrivateTransaction = requestContext.getRequiredParameter(0, String.class);

    try {
      final PrivateTransaction privateTransaction =
          PrivateTransaction.readFrom(RLP.input(Bytes.fromHexString(rawPrivateTransaction)));

      final ValidationResult<TransactionInvalidReason> validationResult =
          validatePrivateTransaction(privateTransaction, user);

      if (!validationResult.isValid()) {
        return new JsonRpcErrorResponse(
            id, convertTransactionInvalidReason(validationResult.getInvalidReason()));
      }

      final org.hyperledger.besu.plugin.data.Address sender =
          privateMarkerTransactionFactory.getSender(
              privateTransaction, privacyIdProvider.getPrivacyUserId(user));
      final Lock lock = stripedLock.get(sender.toShortHexString());
      lock.lock();
      try {

        final Transaction privateMarkerTransaction =
            createPrivateMarkerTransaction(privateTransaction, user);

        return transactionPool
            .addLocalTransaction(privateMarkerTransaction)
            .either(
                () -> new JsonRpcSuccessResponse(id, privateMarkerTransaction.getHash().toString()),
                errorReason -> getJsonRpcErrorResponse(id, errorReason));
      } finally {
        lock.unlock();
      }
    } catch (final JsonRpcErrorResponseException e) {
      return new JsonRpcErrorResponse(id, e.getJsonRpcError());
    } catch (final IllegalArgumentException | RLPException e) {
      LOG.error(e);
      return new JsonRpcErrorResponse(id, DECODE_ERROR);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(id, convertEnclaveInvalidReason(e.getMessage()));
    }
  }

  JsonRpcErrorResponse getJsonRpcErrorResponse(
      final Object id, final TransactionInvalidReason errorReason) {
    if (errorReason.equals(TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT)) {
      return new JsonRpcErrorResponse(id, JsonRpcError.PMT_FAILED_INTRINSIC_GAS_EXCEEDS_LIMIT);
    }
    return new JsonRpcErrorResponse(id, convertTransactionInvalidReason(errorReason));
  }

  protected abstract ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final Optional<User> user);

  protected abstract Transaction createPrivateMarkerTransaction(
      final PrivateTransaction privateTransaction, final Optional<User> user);

  protected Transaction createPrivateMarkerTransaction(
      final Address privacyPrecompileAddress,
      final String pmtPayload,
      final PrivateTransaction privateTransaction,
      final String privacyUserId) {

    final Address sender =
        Address.fromPlugin(
            privateMarkerTransactionFactory.getSender(privateTransaction, privacyUserId));

    final long nonce = publicNonceProvider.getNonce(sender);

    final Optional<Wei> gasPrice = blockchainQueries.gasPrice().map(Wei::of);

    final long gasLimit =
        gasCalculator
            .transactionIntrinsicGasCostAndAccessedState(
                new Transaction.Builder().payload(Bytes.fromBase64String(pmtPayload)).build())
            .getGas()
            .toLong();

    final Transaction unsignedPrivateMarkerTransaction =
        new Transaction.Builder()
            .type(TransactionType.FRONTIER)
            .nonce(nonce)
            .gasPrice(gasPrice.orElse(privateTransaction.getGasPrice()))
            .gasLimit(gasLimit)
            .to(privacyPrecompileAddress)
            .value(Wei.ZERO)
            .payload(Bytes.fromBase64String(pmtPayload))
            .build();

    final Bytes rlpBytes =
        privateMarkerTransactionFactory.create(
            unsignedPrivateMarkerTransaction, privateTransaction, privacyUserId);
    return Transaction.readFrom(rlpBytes);
  }
}
