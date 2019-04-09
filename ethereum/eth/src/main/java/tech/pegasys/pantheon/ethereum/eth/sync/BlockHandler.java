/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface BlockHandler<B> {
  CompletableFuture<List<B>> downloadBlocks(List<BlockHeader> headers);

  CompletableFuture<List<B>> validateAndImportBlocks(List<B> blocks);

  long extractBlockNumber(B block);

  Hash extractBlockHash(B block);

  CompletableFuture<Void> executeParallelCalculations(List<B> blocks);
}
