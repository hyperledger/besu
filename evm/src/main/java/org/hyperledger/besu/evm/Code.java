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
package org.hyperledger.besu.evm;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.JumpDestOperation;
import org.hyperledger.besu.evm.operation.Operation;

import java.util.BitSet;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** Represents EVM code associated with an account. */
public class Code {

  /** The bytes representing the code. */
  private final Bytes bytes;

  /** Used to cache valid jump destinations. */
  private BitSet validJumpDestinations;

  /**
   * Public constructor.
   *
   * @param bytes The byte representation of the code.
   */
  public Code(final Bytes bytes) {
    this.bytes = bytes;
  }

  public Code() {
    this(Bytes.EMPTY);
  }

  /**
   * Returns true if the object is equal to this; otherwise false.
   *
   * @param other The object to compare this with.
   * @return True if the object is equal to this; otherwise false.
   */
  @Override
  public boolean equals(final Object other) {
    if (other == null) return false;
    if (other == this) return true;
    if (!(other instanceof Code)) return false;

    final Code that = (Code) other;
    return this.bytes.equals(that.bytes);
  }

  @Override
  public int hashCode() {
    return bytes.hashCode();
  }

  /**
   * Size of the Code, in bytes
   *
   * @return The number of bytes in the code.
   */
  public int getSize() {
    return bytes.size();
  }

  /**
   * Determine whether a specified destination is a valid jump target.
   *
   * @param evm the EVM executing this code
   * @param frame The current message frame
   * @param destination The destination we're checking for validity.
   * @return Whether or not this location is a valid jump destination.
   */
  public boolean isValidJumpDestination(
      final EVM evm, final MessageFrame frame, final UInt256 destination) {
    if (!destination.fitsInt()) return false;

    final int jumpDestination = destination.intValue();
    if (jumpDestination >= getSize()) return false;

    if (validJumpDestinations == null) {
      // Calculate valid jump destinations
      validJumpDestinations = new BitSet(getSize());
      evm.forEachOperation(
          this,
          (final Operation op, final Integer offset) -> {
            if (op.getOpcode() == JumpDestOperation.OPCODE) {
              validJumpDestinations.set(offset);
            }
          });
    }
    return validJumpDestinations.get(jumpDestination);
  }

  public Bytes getBytes() {
    return bytes;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("bytes", bytes).toString();
  }
}
