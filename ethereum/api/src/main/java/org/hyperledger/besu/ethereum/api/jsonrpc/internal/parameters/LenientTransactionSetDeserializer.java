/*
 * Copyright contributors to Besu.
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

import org.hyperledger.besu.ethereum.core.Transaction;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deserializes an array of encoded transactions to a set, skipping the elements that are {@code
 * null} or cannot be decoded, so that a single invalid transaction does not prevent the others from
 * being returned. Duplicate transactions are returned once, and the order of the array is kept. A
 * value that is not an array still fails deserialization.
 *
 * <p>Meant for the inclusion list transactions (EIP-7805), where a transaction that cannot be
 * decoded is just not considered, see {@link JsonRpcParameter#requiredLenientSet}.
 */
public class LenientTransactionSetDeserializer extends StdDeserializer<Set<Transaction>> {
  private static final Logger LOG =
      LoggerFactory.getLogger(LenientTransactionSetDeserializer.class);

  /** Default constructor. */
  public LenientTransactionSetDeserializer() {
    super(Set.class);
  }

  @Override
  public Set<Transaction> deserialize(final JsonParser parser, final DeserializationContext context)
      throws IOException {
    final JsonNode node = context.readTree(parser);
    if (!node.isArray()) {
      return context.reportInputMismatch(
          this, "Expected an array of transactions, but was: %s", node.getNodeType());
    }

    final Set<Transaction> transactions = LinkedHashSet.newLinkedHashSet(node.size());
    for (int i = 0; i < node.size(); i++) {
      final JsonNode element = node.get(i);
      if (element.isNull()) {
        LOG.debug("Skipping null transaction at index {}", i);
        continue;
      }
      try {
        transactions.add(context.readTreeAsValue(element, Transaction.class));
      } catch (final IOException | RuntimeException e) {
        // the transaction deserializer can throw any runtime exception (e.g. an RLP decoding error)
        LOG.atDebug()
            .setMessage("Skipping transaction at index {}, since it is not valid: '{}'")
            .addArgument(i)
            .addArgument(element)
            .setCause(e)
            .log();
      }
    }
    return transactions;
  }
}
