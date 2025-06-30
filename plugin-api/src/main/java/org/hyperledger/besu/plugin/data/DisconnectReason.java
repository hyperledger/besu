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
package org.hyperledger.besu.plugin.data;

import java.util.Optional;

/**
 * Plugin interface for disconnect reasons.
 * This interface provides plugin access to disconnect reason functionality.
 */
public interface DisconnectReason {

  /**
   * Get the disconnect reason code.
   *
   * @return the optional disconnect reason code
   */
  Optional<Byte> getCode();

  /**
   * Get the disconnect reason message.
   *
   * @return the optional disconnect reason message
   */
  Optional<String> getMessage();

  /**
   * Check if this disconnect reason has a specific code.
   *
   * @param code the code to check
   * @return true if this reason has the specified code
   */
  default boolean hasCode(final byte code) {
    return getCode().map(c -> c == code).orElse(false);
  }

  /**
   * Get a string representation of this disconnect reason.
   *
   * @return string representation
   */
  String getDisplayName();
}
