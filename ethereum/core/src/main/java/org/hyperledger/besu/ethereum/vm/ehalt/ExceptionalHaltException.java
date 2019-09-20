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
package org.hyperledger.besu.ethereum.vm.ehalt;

import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;

import java.util.EnumSet;

/** An exception to signal that an exceptional halt has occurred. */
public class ExceptionalHaltException extends Exception {
  private final EnumSet<ExceptionalHaltReason> reasons;

  public ExceptionalHaltException(final EnumSet<ExceptionalHaltReason> reasons) {
    this.reasons = reasons;
  }

  @Override
  public String getMessage() {
    return "Exceptional halt condition(s) triggered: " + this.reasons;
  }

  public EnumSet<ExceptionalHaltReason> getReasons() {
    return reasons;
  }
}
