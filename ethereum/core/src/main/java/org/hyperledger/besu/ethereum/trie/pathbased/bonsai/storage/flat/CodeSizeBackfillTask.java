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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat;

import org.hyperledger.besu.util.backfill.AbstractBackfillTask;
import org.hyperledger.besu.util.backfill.BackfillRegistry;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CodeSizeBackfillTask extends AbstractBackfillTask {
  private static final Logger LOG = LoggerFactory.getLogger(CodeSizeBackfillTask.class);

  @Inject
  public CodeSizeBackfillTask(final BackfillRegistry backfillRegistry) {
    super(backfillRegistry);
  }

  @Override
  protected void executeUnitOfWork() {
    LOG.info("Starting one unit of code size backfill task");

    try {
      sleepInterruptibly(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Logic to backfill code sizes in the storage
    // This is a placeholder for the actual implementation
    // It should iterate through accounts and update their code sizes as needed

    LOG.info("Code size backfill task completed successfully");
  }

  @Override
  public void requestPause() {
    super.requestPause();
    LOG.info("Code size backfill task requested to pause");
  }

  @Override
  public void resume() {
    super.requestPause();
    LOG.info("Code size backfill task resuming...");
  }

  @Override
  public void stop() {
    super.stop();
    LOG.info("Code size backfill task stopped");
  }
}
