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
package org.hyperledger.besu.cli.presynctasks

import org.hyperledger.besu.controller.BesuController
import java.util.function.Consumer

/** The Pre synchronization task runner.  */
class PreSynchronizationTaskRunner
/** Default Constructor.  */
{
    private val tasks: MutableList<PreSynchronizationTask> = ArrayList()

    /**
     * Add task.
     *
     * @param task the task
     */
    fun addTask(task: PreSynchronizationTask) {
        tasks.add(task)
    }

    /**
     * Run tasks.
     *
     * @param besuController the besu controller
     */
    fun runTasks(besuController: BesuController?) {
        tasks.forEach(Consumer { t: PreSynchronizationTask -> t.run(besuController) })
    }
}
