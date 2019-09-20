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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing;

/**
 * Trace is a marker interface representing different types of Parity style JSON responses for the
 * trace_replayBlockTransactions RPC API. trace_replayBlockTransactions is part of the trace RPC API
 * group. 3 implementations:
 *
 * <ul>
 *   <li>trace: {@link
 *       org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace}
 *   <li>vmTrace: {@link
 *       org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm.VmTrace}
 *   <li>stateDiff:
 * </ul>
 */
public interface Trace {}
