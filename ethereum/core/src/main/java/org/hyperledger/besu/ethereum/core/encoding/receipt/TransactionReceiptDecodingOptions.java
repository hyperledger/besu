/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.core.encoding.receipt;

public class TransactionReceiptDecodingOptions {
  public static final TransactionReceiptDecodingOptions DEFAULT = new Builder().build();

  public static final TransactionReceiptDecodingOptions NO_REVERT_REASON =
      new Builder().withRevertReason(false).build();

  public static final TransactionReceiptDecodingOptions NETWORK_FLAT =
      new Builder().withRevertReason(false).withFlatResponse(true).build();

  private final boolean withRevertReason;
  private final boolean withFlatResponse;

  private TransactionReceiptDecodingOptions(final Builder builder) {
    this.withRevertReason = builder.withRevertReason;
    this.withFlatResponse = builder.withFlatResponse;
  }

  // Getters
  public boolean isWithRevertReason() {
    return withRevertReason;
  }

  public boolean isWithFlatResponse() {
    return withFlatResponse;
  }

  public boolean isRevertReasonAllowed() {
    return withRevertReason && !withFlatResponse;
  }

  public static class Builder {
    private boolean withRevertReason = true;
    private boolean withFlatResponse = false;

    public Builder withRevertReason(final boolean withRevertReason) {
      this.withRevertReason = withRevertReason;
      return this;
    }

    public Builder withFlatResponse(final boolean withFlatResponse) {
      this.withFlatResponse = withFlatResponse;
      return this;
    }

    public TransactionReceiptDecodingOptions build() {
      return new TransactionReceiptDecodingOptions(this);
    }
  }
}
