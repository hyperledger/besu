/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.RequestType;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;

public class Request implements org.hyperledger.besu.plugin.data.Request {
  private final RequestType type;
  private final Bytes data;

  public Request(final RequestType type, final Bytes data) {
    this.type = type;
    this.data = data;
  }

  @Override
  public RequestType getType() {
    return type;
  }

  @Override
  public Bytes getData() {
    return data;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof Request request)) return false;
    return type == request.type && Objects.equals(data, request.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, data);
  }

  @Override
  public String toString() {
    return "Request{" + "type=" + type + ", data=" + data + '}';
  }
}
