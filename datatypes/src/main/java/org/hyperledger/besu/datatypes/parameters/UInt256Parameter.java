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
package org.hyperledger.besu.datatypes.parameters;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.tuweni.units.bigints.UInt256;

/** A parameter that represents a UInt256 value. */
public class UInt256Parameter {

  private final UInt256 value;

  /**
   * Create a new UInt256Parameter
   *
   * @param value the value
   */
  @JsonCreator
  public UInt256Parameter(final String value) {
    this.value = UInt256.fromHexString(value);
  }

  /**
   * Get the value
   *
   * @return the value
   */
  public UInt256 getValue() {
    return value;
  }
}
