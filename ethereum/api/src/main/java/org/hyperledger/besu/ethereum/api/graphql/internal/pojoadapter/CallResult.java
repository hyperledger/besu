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

/**
 * Represents the result of a call execution. This class is used to encapsulate the status, gas
 * used, and data returned by a call execution. It is used in conjunction with the {@link
 * org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.BlockAdapterBase} class.
 *
 * @see org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.BlockAdapterBase
 */
@SuppressWarnings("unused") // reflected by GraphQL
public class CallResult {
  private final Long status;
  private final Long gasUsed;
  private final Bytes data;

  /**
   * Constructs a new CallResult.
   *
   * @param status the status of the call execution
   * @param gasUsed the amount of gas used by the call
   * @param data the data returned by the call
   */
  CallResult(final Long status, final Long gasUsed, final Bytes data) {
    this.status = status;
    this.gasUsed = gasUsed;
    this.data = data;
  }

  /**
   * Returns the status of the call execution.
   *
   * @return the status of the call execution
   */
  public Long getStatus() {
    return status;
  }

  /**
   * Returns the amount of gas used by the call.
   *
   * @return the amount of gas used by the call
   */
  public Long getGasUsed() {
    return gasUsed;
  }

  /**
   * Returns the data returned by the call.
   *
   * @return the data returned by the call
   */
  public Bytes getData() {
    return data;
  }
}
