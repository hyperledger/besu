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
package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.Unstable;

/** The Trace service interface */
@Unstable
public interface TraceService extends BesuService {
  /**
   * Traces a block
   *
   * @param blockNumber the block number
   * @param tracer the tracer (OperationTracer)
   */
  void traceBlock(long blockNumber, OperationTracer tracer);

  /**
   * Traces a block by hash
   *
   * @param hash the block hash
   * @param tracer the tracer (OperationTracer)
   */
  void traceBlock(Hash hash, OperationTracer tracer);
}
