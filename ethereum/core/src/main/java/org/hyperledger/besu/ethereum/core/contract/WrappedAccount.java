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

package org.hyperledger.besu.ethereum.core.contract;

import org.hyperledger.besu.ethereum.core.Account;

/** Wraps any Account for use by the CodeCache */
public class WrappedAccount {

  public Account getWrapped() {
    return wrapped;
  }

  private final Account wrapped;

  public WrappedAccount(final Account wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public int hashCode() {
    return wrapped.getCode().hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    WrappedAccount that = (WrappedAccount) o;
    return wrapped.equals(that.wrapped);
  }
}
