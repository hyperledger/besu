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
package org.hyperledger.besu.ethereum.mainnet.parallelization.preload;

import org.hyperledger.besu.services.tasks.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoOpPreloader implements Preloader {

  private static final Logger LOG = LoggerFactory.getLogger(NoOpPreloader.class);

  @Override
  public void enqueueRequest(final PreloadTask request) {
    LOG.info("NoOpPreloader >> enqueueRequest");
  }

  @Override
  public Task<PreloadTask> dequeueRequest() {
    LOG.info("NoOpPreloader >> dequeueRequest");
    return null;
  }

  @Override
  public void clearQueue() {}
}
