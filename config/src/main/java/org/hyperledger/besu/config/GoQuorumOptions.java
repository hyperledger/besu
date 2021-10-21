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
package org.hyperledger.besu.config;

/**
 * Flag to determine whether we are processing in GoQuorum mode. Note that this mode is incompatible
 * with MainNet.
 */
public class GoQuorumOptions {
  private static final boolean GOQUORUM_COMPATIBILITY_MODE_DEFAULT_VALUE = false;

  private static Boolean goQuorumCompatibilityMode;

  public static void setGoQuorumCompatibilityMode(final boolean goQuorumCompatibilityMode) {
    if (GoQuorumOptions.goQuorumCompatibilityMode == null) {
      GoQuorumOptions.goQuorumCompatibilityMode = goQuorumCompatibilityMode;
    } else {
      throw new IllegalStateException(
          "goQuorumCompatibilityMode can not be changed after having been assigned");
    }
  }

  public static boolean getGoQuorumCompatibilityMode() {
    if (goQuorumCompatibilityMode == null) {
      // If the quorum mode has never been set, we default it
      // here. This allows running individual unit tests that
      // query the quorum mode without having to include a
      // setGoQuorumCompatibilityMode call in their setup
      // procedure. For production use, this case is not
      // triggered as we set the quorum mode very early during
      // startup.
      goQuorumCompatibilityMode = GOQUORUM_COMPATIBILITY_MODE_DEFAULT_VALUE;
    }
    return goQuorumCompatibilityMode;
  }
}
