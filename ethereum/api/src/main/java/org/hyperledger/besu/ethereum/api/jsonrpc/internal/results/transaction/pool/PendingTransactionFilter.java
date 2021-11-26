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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.Predicate.ACTION;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.Predicate.EQ;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class allows to filter a list of pending transactions
 *
 * <p>Here is the list of fields that can be used to filter a transaction : from, to, gas, gasPrice,
 * value, nonce
 */
public class PendingTransactionFilter {

  public static final String FROM_FIELD = "from";
  public static final String TO_FIELD = "to";
  public static final String GAS_FIELD = "gas";
  public static final String GAS_PRICE_FIELD = "gasPrice";
  public static final String VALUE_FIELD = "value";
  public static final String NONCE_FIELD = "nonce";

  public Set<Transaction> reduce(
      final Set<TransactionInfo> pendingTransactions, final List<Filter> filters, final int limit)
      throws InvalidJsonRpcParameters {
    return pendingTransactions.stream()
        .filter(transactionInfo -> applyFilters(transactionInfo, filters))
        .limit(limit)
        .map(TransactionInfo::getTransaction)
        .collect(Collectors.toSet());
  }

  private boolean applyFilters(final TransactionInfo transactionInfo, final List<Filter> filters)
      throws InvalidJsonRpcParameters {
    boolean isValid = true;
    for (Filter filter : filters) {
      final Predicate predicate = filter.getPredicate();
      final String value = filter.getFieldValue();
      switch (filter.getFieldName()) {
        case FROM_FIELD:
          isValid = validateFrom(transactionInfo, predicate, value);
          break;
        case TO_FIELD:
          isValid = validateTo(transactionInfo, predicate, value);
          break;
        case GAS_PRICE_FIELD:
          isValid =
              validateWei(transactionInfo.getTransaction().getGasPrice().get(), predicate, value);
          break;
        case GAS_FIELD:
          isValid =
              validateWei(Wei.of(transactionInfo.getTransaction().getGasLimit()), predicate, value);
          break;
        case VALUE_FIELD:
          isValid = validateWei(transactionInfo.getTransaction().getValue(), predicate, value);
          break;
        case NONCE_FIELD:
          isValid = validateNonce(transactionInfo, predicate, value);
          break;
      }
      if (!isValid) {
        return false;
      }
    }
    return true;
  }

  private boolean validateFrom(
      final TransactionInfo transactionInfo, final Predicate predicate, final String value)
      throws InvalidJsonRpcParameters {
    return predicate
        .getOperator()
        .apply(transactionInfo.getTransaction().getSender(), Address.fromHexString(value));
  }

  private boolean validateTo(
      final TransactionInfo transactionInfo, final Predicate predicate, final String value)
      throws InvalidJsonRpcParameters {
    final Optional<Address> maybeTo = transactionInfo.getTransaction().getTo();
    if (maybeTo.isPresent() && predicate.equals(EQ)) {
      return predicate.getOperator().apply(maybeTo.get(), Address.fromHexString(value));
    } else if (predicate.equals(ACTION)) {
      return transactionInfo.getTransaction().isContractCreation();
    }
    return false;
  }

  private boolean validateNonce(
      final TransactionInfo transactionInfo, final Predicate predicate, final String value)
      throws InvalidJsonRpcParameters {
    return predicate
        .getOperator()
        .apply(transactionInfo.getTransaction().getNonce(), Long.decode(value));
  }

  private boolean validateWei(
      final Wei transactionWei, final Predicate predicate, final String value)
      throws InvalidJsonRpcParameters {
    return predicate.getOperator().apply(transactionWei, Wei.fromHexString(value));
  }

  public static class Filter {

    private final String fieldName;
    private final String fieldValue;
    private final Predicate predicate;

    public Filter(final String fieldName, final String fieldValue, final Predicate predicate) {
      this.fieldName = fieldName;
      this.fieldValue = fieldValue;
      this.predicate = predicate;
    }

    public String getFieldName() {
      return fieldName;
    }

    public String getFieldValue() {
      return fieldValue;
    }

    public Predicate getPredicate() {
      return predicate;
    }
  }
}
