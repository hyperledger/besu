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
package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.util.concurrent.CompletionStage;

public interface DownloadPipelineFactory {

  /**
   * Create a pipeline that, when started, will download and import blocks using the specified sync
   * target.
   *
   * @param target the target the chain download is working to catch up to.
   * @return the created but not yet started pipeline.
   */
  Pipeline<?> createDownloadPipelineForSyncTarget(SyncTarget target);

  CompletionStage<Void> startPipeline(
      EthScheduler scheduler, SyncState syncState, SyncTarget syncTarget, Pipeline<?> pipeline);
}
