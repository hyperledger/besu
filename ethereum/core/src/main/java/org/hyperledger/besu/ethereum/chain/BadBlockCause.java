/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
package org.hyperledger.besu.ethereum.chain;

import org.hyperledger.besu.ethereum.core.Block;

import java.util.Optional;

public class BadBlockCause {
  public enum BadBlockReason {
    // Standard spec-related validation failures
    SPEC_VALIDATION_FAILURE,
    // When an unexpected exception occurs during block processing
    EXCEPTIONAL_BLOCK_PROCESSING,
    // This block is bad because it descends from a bad block
    DESCENDS_FROM_BAD_BLOCK,
  }

  private final BadBlockReason reason;
  private final String description;
  private final Optional<Throwable> exception;

  public static BadBlockCause fromProcessingError(final Throwable t) {
    final String description = t.getLocalizedMessage();
    return new BadBlockCause(
        BadBlockReason.EXCEPTIONAL_BLOCK_PROCESSING, description, Optional.of(t));
  }

  public static BadBlockCause fromBadAncestorBlock(final Block badAncestor) {
    final String description =
        String.format("Descends from bad block %s", badAncestor.toLogString());
    return new BadBlockCause(BadBlockReason.DESCENDS_FROM_BAD_BLOCK, description, Optional.empty());
  }

  public static BadBlockCause fromValidationFailure(final String failureMessage) {
    return new BadBlockCause(
        BadBlockReason.SPEC_VALIDATION_FAILURE, failureMessage, Optional.empty());
  }

  private BadBlockCause(BadBlockReason reason, String description, Optional<Throwable> exception) {
    this.reason = reason;
    this.description = description;
    this.exception = exception;
  }

  public BadBlockReason getReason() {
    return reason;
  }

  public String getDescription() {
    return description;
  }

  public Optional<Throwable> getException() {
    return exception;
  }

  @Override
  public String toString() {
    return "BadBlockCause{"
        + "reason="
        + reason
        + ", description='"
        + description
        + '\''
        + ", exception="
        + exception
        + '}';
  }
}
