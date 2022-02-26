/*
 * Copyright Hyperledger Besu contributors
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonValue;

public class OpaqueTransactionsResult implements TransactionResult {

  private final Set<String> transactionInfoResults;

  public OpaqueTransactionsResult(final Set<String> transactionInfoSet) {
    transactionInfoResults =
        transactionInfoSet.stream().map(String::new).collect(Collectors.toSet());
  }

  @JsonValue
  public Set<String> getResults() {
    return transactionInfoResults;
  }
}
