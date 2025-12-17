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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.tuweni.bytes.Bytes;

/**
 * Represents the result of a state trace for a transaction.
 *
 * <p>This class wraps a {@link StateDiffTrace} and exposes a JSON representation depending on
 * whether diff mode is enabled. In diff mode, the serializer produces "pre" and "post" sections
 * showing state changes. In non-diff mode, only the pre-state is included.
 *
 * <p>Serialization is handled by the {@link Serializer} inner class.
 */
@JsonSerialize(using = StateTraceResult.Serializer.class)
public class StateTraceResult {

  /** The state differences captured during tracing. */
  final StateDiffTrace stateDiffTrace;

  /**
   * Whether to output state differences in diff mode.
   *
   * <p>When {@code true}, the JSON output will contain separate {@code pre} and {@code post}
   * sections. When {@code false}, only pre-state values are included.
   */
  final boolean diffMode;

  /**
   * Creates a new {@link StateTraceResult}.
   *
   * @param stateDiffTrace the captured state differences for accounts
   * @param diffMode whether to produce "pre" and "post" diff output
   */
  public StateTraceResult(final StateDiffTrace stateDiffTrace, final boolean diffMode) {
    this.stateDiffTrace = stateDiffTrace;
    this.diffMode = diffMode;
  }

  /**
   * Custom serializer for {@link StateTraceResult}.
   *
   * <p>Produces JSON in the following forms:
   *
   * <ul>
   *   <li>Diff mode: <code>{ "pre": {...}, "post": {...} }</code>
   *   <li>Non-diff mode: <code>{ address: { balance, code, ... }, ... }</code>
   * </ul>
   *
   * Fields written include:
   *
   * <ul>
   *   <li>{@code balance}
   *   <li>{@code code}
   *   <li>{@code codeHash}
   *   <li>{@code nonce}
   *   <li>{@code storage}
   * </ul>
   */
  public static class Serializer extends StdSerializer<StateTraceResult> {

    public Serializer() {
      super(StateTraceResult.class);
    }

    /**
     * Serialize a {@link StateTraceResult} into JSON.
     *
     * @param result the state trace result
     * @param gen the Jackson JSON generator
     * @param provider the serializer provider
     * @throws IOException if writing JSON fails
     */
    @Override
    public void serialize(
        final StateTraceResult result, final JsonGenerator gen, final SerializerProvider provider)
        throws IOException {

      StateDiffTrace trace = result.stateDiffTrace;
      gen.writeStartObject();

      if (result.diffMode) {
        // In diff mode: write "post" then "pre" sections
        gen.writeObjectFieldStart("post");
        for (var entry : trace.entrySet()) {
          writePostNode(gen, entry.getKey(), entry.getValue());
        }
        gen.writeEndObject();

        gen.writeObjectFieldStart("pre");
        for (var entry : trace.entrySet()) {
          writePreNode(gen, entry.getKey(), entry.getValue());
        }
        gen.writeEndObject();

      } else {
        // In non-diff mode: write only pre-state per account
        for (var entry : trace.entrySet()) {
          writeNode(gen, entry.getKey(), entry.getValue());
        }
      }
      gen.writeEndObject();
    }

    /** Writes a full account node (non-diff mode). Uses the "from" values of each diff node. */
    private void writeNode(
        final JsonGenerator gen, final String addr, final AccountDiff accountDiff)
        throws IOException {
      if (shouldSkipNode(accountDiff)) {
        // Skip if all pre-state fields are empty
        return;
      }
      gen.writeObjectFieldStart(addr);

      // Balance
      var fromBalance = accountDiff.getBalance().getFrom();
      if (fromBalance.isPresent()) {
        gen.writeStringField("balance", fromBalance.get());
      }

      // Code (skip if empty bytecode)
      var fromCode = accountDiff.getCode().getFrom();
      if (fromCode.isPresent()) {
        Bytes codeBytes = Bytes.fromHexString(fromCode.get());
        if (!codeBytes.isEmpty()) {
          gen.writeStringField("code", fromCode.get());
        }
      }

      // Code hash (skip if empty hash)
      var fromCodeHash = accountDiff.getCodeHash().getFrom();
      if (fromCodeHash.isPresent()) {
        Hash hash = Hash.fromHexString(fromCodeHash.get());
        if (!Hash.EMPTY.equals(hash)) {
          gen.writeStringField("codeHash", fromCodeHash.get());
        }
      }

      // Nonce (skip if zero)
      if (accountDiff.getNonce().getFrom().isPresent()) {
        String nonceStr = accountDiff.getNonce().getFrom().get();
        try {
          long nonce = Long.decode(nonceStr);
          if (nonce != 0) {
            gen.writeNumberField("nonce", nonce);
          }
        } catch (NumberFormatException e) {
          gen.writeStringField("nonce", nonceStr);
        }
      }

      // Storage entries
      var storageEntries = accountDiff.getStorage().entrySet().stream().toList();
      if (!storageEntries.isEmpty()) {
        gen.writeObjectFieldStart("storage");
        for (var se : storageEntries) {
          DiffNode node = se.getValue();
          gen.writeStringField(se.getKey(), node.getFrom().get());
        }
        gen.writeEndObject();
      }
      gen.writeEndObject();
    }

    /** Writes the "pre" state for an account (diff mode). Only includes fields that differ. */
    private void writePreNode(
        final JsonGenerator gen, final String addr, final AccountDiff accountDiff)
        throws IOException {

      if (!accountDiff.hasDifference() || shouldSkipNode(accountDiff)) {
        return;
      }

      gen.writeObjectFieldStart(addr);

      // Balance, Code, CodeHash, Nonce (same logic as writeNode)
      var fromBalance = accountDiff.getBalance().getFrom();
      if (fromBalance.isPresent()) {
        gen.writeStringField("balance", fromBalance.get());
      }

      var fromCode = accountDiff.getCode().getFrom();
      if (fromCode.isPresent()) {
        if (!Bytes.fromHexString(fromCode.get()).isEmpty()) {
          gen.writeStringField("code", fromCode.get());
        }
      }

      var fromCodeHash = accountDiff.getCodeHash().getFrom();
      if (fromCodeHash.isPresent()) {
        if (!Hash.EMPTY.toHexString().equals(fromCodeHash.get())) {
          gen.writeStringField("codeHash", fromCodeHash.get());
        }
      }

      if (accountDiff.getNonce().getFrom().isPresent()) {
        String nonceStr = accountDiff.getNonce().getFrom().get();
        try {
          long nonce = Long.decode(nonceStr);
          if (nonce != 0) {
            gen.writeNumberField("nonce", nonce);
          }
        } catch (NumberFormatException e) {
          gen.writeStringField("nonce", nonceStr);
        }
      }

      // Storage: include only non-empty "from" entries
      var storageEntries =
          accountDiff.getStorage().entrySet().stream()
              .filter(se -> hasNonEmptyEntry(se.getValue(), DiffNode::getFrom))
              .toList();

      if (!storageEntries.isEmpty()) {
        gen.writeObjectFieldStart("storage");
        for (var se : storageEntries) {
          DiffNode node = se.getValue();
          gen.writeStringField(se.getKey(), node.getFrom().get());
        }
        gen.writeEndObject();
      }
      gen.writeEndObject();
    }

    /**
     * Writes the "post" state for an account (diff mode). Includes only changed and non-zero
     * fields.
     */
    private void writePostNode(
        final JsonGenerator gen, final String addr, final AccountDiff accountDiff)
        throws IOException {

      if (!accountDiff.hasDifference() || !hasNonZeroDifference(accountDiff)) {
        return;
      }

      gen.writeObjectFieldStart(addr);

      // Balance (skip if newly created with zero balance)
      if (accountDiff.getBalance().hasDifference()) {
        String value = accountDiff.getBalance().getTo().get();
        if (!(accountDiff.getBalance().getFrom().isEmpty()
            && Bytes.fromHexStringLenient(value).isZero())) {
          gen.writeStringField("balance", value);
        }
      }

      // Code
      if (accountDiff.getCode().hasDifference()) {
        String code = accountDiff.getCode().getTo().get();
        if (!Bytes.fromHexString(code).isEmpty()) {
          gen.writeStringField("code", code);
        }
      }

      // Code hash
      if (accountDiff.getCodeHash().hasDifference()) {
        String codeHash = accountDiff.getCodeHash().getTo().get();
        if (!Hash.EMPTY.toHexString().equals(codeHash)) {
          gen.writeStringField("codeHash", codeHash);
        }
      }

      // Nonce
      if (accountDiff.getNonce().hasDifference()) {
        String nonceStr = accountDiff.getNonce().getTo().get();
        try {
          long nonce = Long.decode(nonceStr);
          if (nonce != 0) {
            gen.writeNumberField("nonce", nonce);
          }
        } catch (NumberFormatException e) {
          gen.writeStringField("nonce", nonceStr);
        }
      }

      // Storage: include only non-empty "to" entries
      var storageEntries =
          accountDiff.getStorage().entrySet().stream()
              .filter(se -> hasNonEmptyEntry(se.getValue(), DiffNode::getTo))
              .toList();

      if (!storageEntries.isEmpty()) {
        gen.writeObjectFieldStart("storage");
        for (var se : storageEntries) {
          DiffNode node = se.getValue();
          gen.writeStringField(se.getKey(), node.getTo().get());
        }
        gen.writeEndObject();
      }
      gen.writeEndObject();
    }

    /** Returns true if an account has no pre-state values worth serializing. */
    private boolean shouldSkipNode(final AccountDiff accountDiff) {
      return isNodeEmpty(accountDiff, DiffNode::getFrom);
    }

    /** Returns true if the account has meaningful post-state changes (not just zero/empty). */
    private boolean hasNonZeroDifference(final AccountDiff accountDiff) {
      return isFieldPresentAndNonZero(accountDiff.getBalance(), DiffNode::getTo)
          || isFieldPresent(accountDiff.getCode(), DiffNode::getTo)
          || isFieldPresent(accountDiff.getNonce(), DiffNode::getTo)
          || hasStorage(accountDiff.getStorage(), DiffNode::getTo);
    }

    /** Returns true if all fields are absent for the given extractor (from/to). */
    private boolean isNodeEmpty(
        final AccountDiff accountDiff, final Function<DiffNode, Optional<String>> extractor) {
      return isFieldEmpty(accountDiff.getBalance(), extractor)
          && isFieldEmpty(accountDiff.getCode(), extractor)
          && isFieldEmpty(accountDiff.getNonce(), extractor)
          && !hasStorage(accountDiff.getStorage(), extractor);
    }

    private boolean isFieldEmpty(
        final DiffNode node, final Function<DiffNode, Optional<String>> extractor) {
      return node == null || extractor.apply(node).isEmpty();
    }

    private boolean isFieldPresent(
        final DiffNode node, final Function<DiffNode, Optional<String>> extractor) {
      return node != null && extractor.apply(node).isPresent();
    }

    private boolean isFieldPresentAndNonZero(
        final DiffNode node, final Function<DiffNode, Optional<String>> extractor) {
      return extractor.apply(node).filter(value -> !Wei.fromHexString(value).isZero()).isPresent();
    }

    private boolean hasStorage(
        final Map<String, DiffNode> storage, final Function<DiffNode, Optional<String>> extractor) {
      return storage != null
          && storage.values().stream().anyMatch(node -> isFieldPresent(node, extractor));
    }

    /** Returns true if the given storage entry has a non-empty, non-zero value. */
    private boolean hasNonEmptyEntry(
        final DiffNode node, final Function<DiffNode, Optional<String>> extractor) {
      if (node == null) return false;
      return extractor.apply(node).filter(v -> !Bytes.fromHexStringLenient(v).isZero()).isPresent();
    }
  }
}
