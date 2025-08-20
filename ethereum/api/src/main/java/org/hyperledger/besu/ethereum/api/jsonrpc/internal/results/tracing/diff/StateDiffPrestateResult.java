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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.diff;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

@JsonSerialize(using = StateDiffPrestateResult.Serializer.class)
public class StateDiffPrestateResult {
  final StateDiffTrace stateDiffTrace;

  public StateDiffPrestateResult(final StateDiffTrace stateDiffTrace) {
    this.stateDiffTrace = stateDiffTrace;
  }

  /**
   * Custom serializer for StateDiffTracePrestate. Produces JSON in the following format: { "pre": {
   * ... all "from" values of DiffNodes ... }, "post": { ... all "to" values of DiffNodes that have
   * changed ... } }
   */
  public static class Serializer extends StdSerializer<StateDiffPrestateResult> {

    public Serializer() {
      super(StateDiffPrestateResult.class);
    }

    /**
     * Serialize the StateDiffTracePrestate to JSON. Generates two sections: "pre" and "post".
     *
     * @param result the result object
     * @param gen JsonGenerator used to write JSON
     * @param provider serializer provider
     */
    @Override
    public void serialize(
        final StateDiffPrestateResult result,
        final JsonGenerator gen,
        final SerializerProvider provider)
        throws IOException {
      StateDiffTrace trace = result.stateDiffTrace;

      gen.writeStartObject();

      // Serialize "pre" section: all 'from' values
      gen.writeObjectFieldStart("pre");
      for (var entry : trace.entrySet()) {
        writeNode(gen, entry.getKey(), entry.getValue(), true);
      }
      gen.writeEndObject();

      // Serialize "post" section: only 'to' values that differ
      gen.writeObjectFieldStart("post");
      for (var entry : trace.entrySet()) {
        writeNode(gen, entry.getKey(), entry.getValue(), false);
      }
      gen.writeEndObject();

      gen.writeEndObject();
    }

    /**
     * Write a single account node for pre/post sections.
     *
     * @param gen JsonGenerator
     * @param addr account address
     * @param accountDiff the account diff
     * @param pre if true, write 'from' values; if false, write 'to' values that changed
     */
    private void writeNode(
        final JsonGenerator gen,
        final String addr,
        final AccountDiff accountDiff,
        final boolean pre)
        throws IOException {

      gen.writeObjectFieldStart(addr);

      if (shouldWriteNode(accountDiff.getBalance(), pre)) {
        gen.writeStringField(
            "balance",
            pre
                ? accountDiff.getBalance().getFrom().get()
                : accountDiff.getBalance().getTo().get());
      }
      if (shouldWriteNode(accountDiff.getCode(), pre)) {
        gen.writeStringField(
            "code",
            pre ? accountDiff.getCode().getFrom().get() : accountDiff.getCode().getTo().get());
      }
      if (shouldWriteNode(accountDiff.getNonce(), pre)) {
        gen.writeStringField(
            "nonce",
            pre ? accountDiff.getNonce().getFrom().get() : accountDiff.getNonce().getTo().get());
      }

      // Serialize storage map for this account
      writeStorage(gen, accountDiff.getStorage(), pre);

      gen.writeEndObject();
    }

    /**
     * Write the storage map for an account.
     *
     * @param gen JsonGenerator
     * @param storage map of storage keys to DiffNodes
     * @param pre if true, write 'from' values; if false, write 'to' values that changed
     */
    private void writeStorage(
        final JsonGenerator gen, final Map<String, DiffNode> storage, final boolean pre)
        throws IOException {

      if (storage == null || storage.isEmpty()) return;

      gen.writeObjectFieldStart("storage");
      for (var se : storage.entrySet()) {
        DiffNode node = se.getValue();
        if (shouldWriteNode(node, pre)) {
          gen.writeStringField(se.getKey(), pre ? node.getFrom().get() : node.getTo().get());
        }
      }
      gen.writeEndObject();
    }

    /**
     * Whether a DiffNode should be written.
     *
     * @param node the DiffNode
     * @param pre true = writing 'pre' section; false = writing 'post' section
     * @return true if the node should be serialized
     */
    private boolean shouldWriteNode(final DiffNode node, final boolean pre) {
      if (node == null) return false;
      if (pre) return node.getFrom().isPresent();
      return node.hasDifference() && node.getTo().isPresent();
    }
  }
}
