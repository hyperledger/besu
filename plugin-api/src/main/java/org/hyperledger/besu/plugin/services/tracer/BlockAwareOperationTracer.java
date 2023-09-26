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
 */
package org.hyperledger.besu.plugin.services.tracer;

import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;

/**
 * An extended operation tracer that can trace the start and end of a block.
 *
 * <p>In both methods, the block header and body are provided.
 */
public interface BlockAwareOperationTracer extends OperationTracer {
  /**
   * Trace the start of a block.
   *
   * @param blockHeader the header of the block which is traced
   * @param blockBody the body of the block which is traced
   */
  default void traceStartBlock(final BlockHeader blockHeader, final BlockBody blockBody) {}

  /**
   * Trace the end of a block.
   *
   * @param blockHeader the header of the block which is traced
   * @param blockBody the body of the block which is traced
   */
  default void traceEndBlock(final BlockHeader blockHeader, final BlockBody blockBody) {}

  @Override
  default boolean isExtendedTracing() {
    return true;
  }
}
