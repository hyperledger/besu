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
package org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter;

import org.apache.tuweni.bytes.Bytes;

/** The type Call result. */
@SuppressWarnings("unused") // reflected by GraphQL
public class CallResult {
  private final Long status;
  private final Long gasUsed;
  private final Bytes data;

  /**
   * Instantiates a new Call result.
   *
   * @param status the status
   * @param gasUsed the gas used
   * @param data the data
   */
  CallResult(final Long status, final Long gasUsed, final Bytes data) {
    this.status = status;
    this.gasUsed = gasUsed;
    this.data = data;
  }

  /**
   * Gets status.
   *
   * @return the status
   */
  public Long getStatus() {
    return status;
  }

  /**
   * Gets gas used.
   *
   * @return the gas used
   */
  public Long getGasUsed() {
    return gasUsed;
  }

  /**
   * Gets data.
   *
   * @return the data
   */
  public Bytes getData() {
    return data;
  }
}
