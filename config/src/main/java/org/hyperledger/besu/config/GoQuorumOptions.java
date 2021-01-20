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
  // To make it easier for tests to reset the value to default
  public static final boolean GOQUORUM_COMPATIBILITY_MODE_DEFAULT_VALUE = false;

  public static boolean goQuorumCompatibilityMode = GOQUORUM_COMPATIBILITY_MODE_DEFAULT_VALUE;
}
