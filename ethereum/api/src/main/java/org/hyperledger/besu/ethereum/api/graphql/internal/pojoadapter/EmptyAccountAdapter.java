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

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;

import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

@SuppressWarnings("unused") // reflected by GraphQL
public class EmptyAccountAdapter extends AdapterBase {
  private final Address address;

  public EmptyAccountAdapter(final Address address) {
    this.address = address;
  }

  public Optional<Address> getAddress() {
    return Optional.of(address);
  }

  public Optional<Wei> getBalance() {
    return Optional.of(Wei.ZERO);
  }

  public Optional<Long> getTransactionCount() {
    return Optional.of(0L);
  }

  public Optional<Bytes> getCode() {
    return Optional.of(Bytes.EMPTY);
  }

  public Optional<Bytes32> getStorage(final DataFetchingEnvironment environment) {
    return Optional.of(Bytes32.ZERO);
  }
}
