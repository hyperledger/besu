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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Represents an empty account in the Ethereum blockchain. This class is used when an account does
 * not exist at a specific address. It provides default values for the account's properties. It
 * extends the {@link org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.AccountAdapter}
 * class.
 *
 * @see org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.AccountAdapter
 */
public class EmptyAccountAdapter extends AccountAdapter {
  private final Address address;

  /**
   * Constructs a new EmptyAccountAdapter.
   *
   * @param address the address of the account
   */
  public EmptyAccountAdapter(final Address address) {
    super(null);
    this.address = address;
  }

  @Override
  public Address getAddress() {
    return address;
  }

  @Override
  public Wei getBalance() {
    return Wei.ZERO;
  }

  @Override
  public Long getTransactionCount() {
    return 0L;
  }

  @Override
  public Bytes getCode(final DataFetchingEnvironment environment) {
    return Bytes.EMPTY;
  }

  @Override
  public Bytes32 getStorage(final DataFetchingEnvironment environment) {
    return Bytes32.ZERO;
  }
}
