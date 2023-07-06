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
package org.hyperledger.besu.cli.presynctasks;

import org.hyperledger.besu.Runner;
import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.controller.BesuController;

/**
 * All PreSynchronizationTask instances execute after the {@link BesuController} instance in {@link
 * BesuCommand}* is ready and before {@link Runner#startEthereumMainLoop()} is called
 */
public interface PreSynchronizationTask {

  /**
   * Run.
   *
   * @param besuController the besu controller
   */
  void run(final BesuController besuController);
}
