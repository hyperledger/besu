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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(NON_NULL)
@JsonPropertyOrder({"address", "code", "gasUsed", "output"})
public class Result {
  private final String gasUsed;
  private final String output;
  private final String code;

  public Result(
      final String gasUsed, final String output, final String code, final String address) {
    this.gasUsed = gasUsed;
    this.output = output;
    this.code = code;
    this.address = address;
  }

  private final String address;

  public String getGasUsed() {
    return gasUsed;
  }

  public String getOutput() {
    return output;
  }

  public String getCode() {
    return code;
  }

  public String getAddress() {
    return address;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private static final String GAS_USED_EMPTY = "0x0";

    private String gasUsed = GAS_USED_EMPTY;
    private String output = "0x";
    private String code;
    private String address;

    private Builder() {}

    public Builder gasUsed(final String gasUsed) {
      this.gasUsed = gasUsed;
      return this;
    }

    public boolean isGasUsedEmpty() {
      return GAS_USED_EMPTY.equals(gasUsed);
    }

    public Builder output(final String output) {
      this.output = output;
      return this;
    }

    public Builder code(final String code) {
      this.code = code;
      this.output = null;
      return this;
    }

    public String getCode() {
      return code;
    }

    public Builder address(final String address) {
      this.address = address;
      return this;
    }

    public String getAddress() {
      return address;
    }

    public static Builder of(final Result result) {
      final Builder builder = new Builder();
      if (result != null) {
        builder.gasUsed = result.gasUsed;
        builder.output = result.output;
        builder.code = result.code;
        builder.address = result.address;
      }
      return builder;
    }

    public Result build() {
      return new Result(gasUsed, output, code, address);
    }
  }
}
