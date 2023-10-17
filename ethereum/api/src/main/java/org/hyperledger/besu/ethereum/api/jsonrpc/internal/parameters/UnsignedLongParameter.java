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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.annotation.JsonCreator;

public class UnsignedLongParameter {

  private final long value;

  @JsonCreator
  public UnsignedLongParameter(final String value) {
    checkArgument(value != null);
    if (value.startsWith("0x")) {
      this.value = Long.parseUnsignedLong(value.substring(2), 16);
    } else {
      this.value = Long.parseUnsignedLong(value, 16);
    }
  }

  @JsonCreator
  public UnsignedLongParameter(final long value) {
    this.value = value;
  }

  public long getValue() {
    return value;
  }
}
