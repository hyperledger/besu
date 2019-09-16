/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter;

import org.hyperledger.besu.util.bytes.BytesValue;

@SuppressWarnings("unused") // reflected by GraphQL
class CallResult {
  private final Long status;
  private final Long gasUsed;
  private final BytesValue data;

  CallResult(final Long status, final Long gasUsed, final BytesValue data) {
    this.status = status;
    this.gasUsed = gasUsed;
    this.data = data;
  }

  public Long getStatus() {
    return status;
  }

  public Long getGasUsed() {
    return gasUsed;
  }

  public BytesValue getData() {
    return data;
  }
}
