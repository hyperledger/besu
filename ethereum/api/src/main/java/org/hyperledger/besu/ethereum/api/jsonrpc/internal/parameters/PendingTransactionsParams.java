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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.PendingTransactionFilter.FROM_FIELD;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.PendingTransactionFilter.GAS_FIELD;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.PendingTransactionFilter.GAS_PRICE_FIELD;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.PendingTransactionFilter.NONCE_FIELD;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.PendingTransactionFilter.TO_FIELD;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.PendingTransactionFilter.VALUE_FIELD;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.Predicate.ACTION;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.Predicate.EQ;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.PendingTransactionFilter.Filter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PendingTransactionsParams {

  private final Map<String, String> from, to, gas, gasPrice, value, nonce;

  @JsonCreator()
  public PendingTransactionsParams(
      @JsonProperty(FROM_FIELD) final Map<String, String> from,
      @JsonProperty(TO_FIELD) final Map<String, String> to,
      @JsonProperty(GAS_FIELD) final Map<String, String> gas,
      @JsonProperty(GAS_PRICE_FIELD) final Map<String, String> gasPrice,
      @JsonProperty(VALUE_FIELD) final Map<String, String> value,
      @JsonProperty(NONCE_FIELD) final Map<String, String> nonce) {
    this.from = from;
    this.to = to;
    this.gas = gas;
    this.gasPrice = gasPrice;
    this.value = value;
    this.nonce = nonce;
  }

  public List<Filter> filters() throws IllegalArgumentException {
    final List<Filter> createdFilters = new ArrayList<>();
    getFilter(FROM_FIELD, from).ifPresent(createdFilters::add);
    getFilter(TO_FIELD, to).ifPresent(createdFilters::add);
    getFilter(GAS_FIELD, gas).ifPresent(createdFilters::add);
    getFilter(GAS_PRICE_FIELD, gasPrice).ifPresent(createdFilters::add);
    getFilter(VALUE_FIELD, value).ifPresent(createdFilters::add);
    getFilter(NONCE_FIELD, nonce).ifPresent(createdFilters::add);
    return createdFilters;
  }

  /**
   * This method allows to retrieve a list of filters related to a key
   *
   * @param key the key that will be linked to the filters
   * @param map the list of filters to parse
   * @return the list of filters
   */
  private Optional<Filter> getFilter(final String key, final Map<String, String> map) {
    if (map != null) {
      if (map.size() > 1) {
        throw new InvalidJsonRpcParameters("Only one operator per filter type allowed");
      } else if (!map.isEmpty()) {
        final Map.Entry<String, String> foundEntry = map.entrySet().stream().findFirst().get();
        final Predicate predicate =
            Predicate.fromValue(foundEntry.getKey().toUpperCase())
                .orElseThrow(
                    () ->
                        new InvalidJsonRpcParameters(
                            "Unknown field expected one of `eq`, `gt`, `lt`, `action`"));

        final Filter filter = new Filter(key, foundEntry.getValue(), predicate);
        if (key.equals(FROM_FIELD) && !predicate.equals(EQ)) {
          throw new InvalidJsonRpcParameters("The `from` filter only supports the `eq` operator");
        } else if (key.equals(TO_FIELD) && !predicate.equals(EQ) && !predicate.equals(ACTION)) {
          throw new InvalidJsonRpcParameters(
              "The `to` filter only supports the `eq` or `action` operator");
        } else if (!key.equals(TO_FIELD) && predicate.equals(ACTION)) {
          throw new InvalidJsonRpcParameters(
              "The operator `action` is only supported by the `to` filter");
        }
        return Optional.of(filter);
      }
    }
    return Optional.empty();
  }
}
