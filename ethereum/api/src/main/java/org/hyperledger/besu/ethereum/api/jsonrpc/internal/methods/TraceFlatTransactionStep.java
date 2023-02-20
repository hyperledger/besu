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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.function.Function;
import java.util.stream.Stream;

public class TraceFlatTransactionStep implements Function<TransactionTrace, Stream<Trace>> {

  private final ProtocolSchedule protocolSchedule;
  private final Block block;

  public TraceFlatTransactionStep(final ProtocolSchedule protocolSchedule, final Block block) {
    this.protocolSchedule = protocolSchedule;
    this.block = block;
  }

  @Override
  public Stream<Trace> apply(final TransactionTrace transactionTrace) {
    return FlatTraceGenerator.generateFromTransactionTraceAndBlock(
        protocolSchedule, transactionTrace, block);
  }
}
