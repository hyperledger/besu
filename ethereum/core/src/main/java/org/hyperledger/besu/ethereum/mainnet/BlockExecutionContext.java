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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.plugin.services.worldstate.MutableWorldState;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Encapsulates all inputs required to execute a block via {@link BlockProcessor#processBlock}.
 *
 * <p>All four fields are required. The blockchain is obtained from {@code protocolContext}. The
 * block access list must be set even when there is none, with {@link Optional#empty()}: without it,
 * an Amsterdam block loses its BAL-based parallel execution and prefetch, with nothing failing, so
 * leaving it out must be a decision rather than an omission.
 *
 * <p>Sequential execution can optionally be requested for this execution, see {@link
 * Builder#sequentialExecutionRequested(boolean)}.
 *
 * <p>Construct instances via {@link #builder()}.
 */
public class BlockExecutionContext {

  private final ProtocolContext protocolContext;
  private final MutableWorldState worldState;
  private final Block block;
  @Nullable private final BlockAccessList blockAccessList;
  private final boolean sequentialExecutionRequested;

  private BlockExecutionContext(final Builder builder) {
    this.protocolContext = builder.protocolContext;
    this.worldState = builder.worldState;
    this.block = builder.block;
    this.blockAccessList = builder.blockAccessList;
    this.sequentialExecutionRequested = builder.sequentialExecutionRequested;
  }

  /**
   * Returns the current protocol context.
   *
   * @return the current protocol context
   */
  public ProtocolContext getProtocolContext() {
    return protocolContext;
  }

  /**
   * Returns the mutable world state to apply changes to.
   *
   * @return the mutable world state to apply changes to
   */
  public MutableWorldState getWorldState() {
    return worldState;
  }

  /**
   * Returns the block to process.
   *
   * @return the block to process
   */
  public Block getBlock() {
    return block;
  }

  /**
   * Returns an optional pre-computed block access list to validate against.
   *
   * @return an optional pre-computed block access list to validate against, or empty to construct
   *     one during execution
   */
  public Optional<BlockAccessList> getBlockAccessList() {
    return Optional.ofNullable(blockAccessList);
  }

  /**
   * Returns whether the caller requested sequential execution for this execution of the block. See
   * {@link Builder#sequentialExecutionRequested(boolean)}.
   *
   * @return {@code true} if the block's transactions must be executed in order only
   */
  public boolean isSequentialExecutionRequested() {
    return sequentialExecutionRequested;
  }

  /**
   * Returns a {@link Builder} initialized with this context's fields.
   *
   * @return a new {@link Builder} copying this context
   */
  public Builder toBuilder() {
    return builder()
        .protocolContext(protocolContext)
        .worldState(worldState)
        .block(block)
        .blockAccessList(getBlockAccessList())
        .sequentialExecutionRequested(sequentialExecutionRequested);
  }

  /**
   * Returns a new {@link Builder}.
   *
   * @return a new {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link BlockExecutionContext}. */
  public static class Builder {

    private ProtocolContext protocolContext;
    private MutableWorldState worldState;
    private Block block;
    @Nullable private BlockAccessList blockAccessList = null;
    private boolean blockAccessListSet = false;
    private boolean sequentialExecutionRequested = false;

    /**
     * Sets the protocol context (required).
     *
     * @param protocolContext the current protocol context (required)
     * @return this builder
     */
    public Builder protocolContext(final ProtocolContext protocolContext) {
      this.protocolContext = protocolContext;
      return this;
    }

    /**
     * Sets the world state (required).
     *
     * @param worldState the world state to apply changes to (required)
     * @return this builder
     */
    public Builder worldState(final MutableWorldState worldState) {
      this.worldState = worldState;
      return this;
    }

    /**
     * Sets the block to process (required).
     *
     * @param block the block to process (required)
     * @return this builder
     */
    public Builder block(final Block block) {
      this.block = block;
      return this;
    }

    /**
     * Sets the pre-computed block access list (required, possibly empty).
     *
     * @param blockAccessList a pre-computed block access list to validate against, or empty if
     *     there is none
     * @return this builder
     */
    public Builder blockAccessList(final Optional<BlockAccessList> blockAccessList) {
      this.blockAccessList =
          Objects.requireNonNull(blockAccessList, "blockAccessList").orElse(null);
      this.blockAccessListSet = true;
      return this;
    }

    /**
     * Sets whether the block's transactions must be executed in order only, without speculative
     * parallel execution (optional, defaults to {@code false}).
     *
     * <p>Applies only to this execution of this block. It does not change the node's parallel
     * transaction processing setting, and processors that never execute in parallel ignore it.
     *
     * @param sequentialExecutionRequested {@code true} to request sequential execution
     * @return this builder
     */
    public Builder sequentialExecutionRequested(final boolean sequentialExecutionRequested) {
      this.sequentialExecutionRequested = sequentialExecutionRequested;
      return this;
    }

    /**
     * Builds the {@link BlockExecutionContext}.
     *
     * @return the constructed context
     * @throws IllegalStateException if any required field is missing
     */
    public BlockExecutionContext build() {
      if (protocolContext == null) throw new IllegalStateException("protocolContext is required");
      if (worldState == null) throw new IllegalStateException("worldState is required");
      if (block == null) throw new IllegalStateException("block is required");
      if (!blockAccessListSet) {
        throw new IllegalStateException(
            "blockAccessList is required: pass Optional.empty() if there is none");
      }
      return new BlockExecutionContext(this);
    }
  }
}
