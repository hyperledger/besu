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

import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

/** The result set from querying the receipts for a given block. */
public class BlockReceiptsResult {

  private final List<TransactionReceiptResult> results;

  public BlockReceiptsResult(final List<TransactionReceiptWithMetadata> receipts) {
    results = new ArrayList<>(receipts.size());

    for (final TransactionReceiptWithMetadata receipt : receipts) {
      /* TODO - root result or status result? */
      results.add(new TransactionReceiptRootResult(receipt));
    }
  }

  @JsonValue
  public List<TransactionReceiptResult> getResults() {
    return results;
  }
}
