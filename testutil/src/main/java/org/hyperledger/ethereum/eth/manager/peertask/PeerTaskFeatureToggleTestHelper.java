/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.ethereum.eth.manager.peertask;

import java.lang.reflect.Field;

import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskFeatureToggle;
import org.junit.platform.commons.util.ReflectionUtils;

public class PeerTaskFeatureToggleTestHelper {

  public static void setPeerTaskFeatureToggle(final boolean usePeerTaskSystem)
      throws IllegalAccessException {
    Field usePeerTaskSystemField =
        ReflectionUtils.findFields(
                PeerTaskFeatureToggle.class,
                (f) -> f.getName().equals("USE_PEER_TASK_SYSTEM"),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
            .getFirst();
    usePeerTaskSystemField.setAccessible(true);
    usePeerTaskSystemField.set(null, usePeerTaskSystem);
  }
}
