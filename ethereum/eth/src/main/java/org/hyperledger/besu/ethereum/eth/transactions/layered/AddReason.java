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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import java.util.Locale;

/** Describe why we are trying to add a tx to a layer. */
public enum AddReason {
  /** When adding a tx, that is not present in the pool. */
  NEW(true, true),
  /** When adding a tx as result of an internal move between layers. */
  MOVE(false, false),
  /** When adding a tx as result of a promotion from a lower layer. */
  PROMOTED(false, false);

  private final boolean sendNotification;
  private final boolean makeCopy;
  private final String label;

  AddReason(final boolean sendNotification, final boolean makeCopy) {
    this.sendNotification = sendNotification;
    this.makeCopy = makeCopy;
    this.label = name().toLowerCase(Locale.ROOT);
  }

  /**
   * Should we send add notification for this reason?
   *
   * @return true if notification should be sent
   */
  public boolean sendNotification() {
    return sendNotification;
  }

  /**
   * Should the layer make a copy of the pending tx before adding it, to avoid keeping reference to
   * potentially large underlying byte buffers?
   *
   * @return true is a copy is necessary
   */
  public boolean makeCopy() {
    return makeCopy;
  }

  /**
   * Return a label that identify this reason to be used in the metric system.
   *
   * @return a label
   */
  public String label() {
    return label;
  }
}
