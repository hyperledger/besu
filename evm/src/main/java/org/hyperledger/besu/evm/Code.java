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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.code.CodeSection;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/** Represents EVM code associated with an account. */
public interface Code {

  /**
   * Size of the code in bytes. This is for the whole container, not just the code section in
   * formats that have sections.
   *
   * @return size of code in bytes.
   */
  int getSize();

  /**
   * Size of the data in bytes. This is for the data only,
   *
   * @return size of code in bytes.
   */
  int getDataSize();

  /**
   * Declared size of the data in bytes. For containers with aux data this may be larger.
   *
   * @return the declared data size
   */
  int getDeclaredDataSize();

  /**
   * Get the bytes for the entire container, for example what EXTCODECOPY would want. For V0 it is
   * the same as getCodeBytes, for V1 it is the entire container, not just the data section.
   *
   * @return container bytes.
   */
  Bytes getBytes();

  /**
   * Hash of the entire container
   *
   * @return hash of the code.
   */
  Hash getCodeHash();

  /**
   * For V0 and V1, is the target jump location valid?
   *
   * @param jumpDestination index from PC=0. Code section for v1, whole container in V0
   * @return true if the operation is both a valid opcode and a JUMPDEST
   */
  boolean isJumpDestInvalid(final int jumpDestination);

  /**
   * Code is considered valid by the EVM.
   *
   * @return isValid
   */
  boolean isValid();

  /**
   * The Code Section Info associated with a code section. If the code does not support sections or
   * an out-of-section code is requested null will be returned.
   *
   * @param section the section number to retrieve.
   * @return The code section, or null of there is no associated section
   */
  CodeSection getCodeSection(final int section);

  /**
   * The number of code sections in this container.
   *
   * @return 1 for legacy, count for valid, zero for invalid.
   */
  int getCodeSectionCount();

  /**
   * Returns the EOF version of the code. Legacy code is version 0, invalid code -1.
   *
   * @return The version of hte ode.
   */
  int getEofVersion();

  /**
   * Returns the count of subcontainers, or zero if there are none or if the code version does not
   * support subcontainers.
   *
   * @return The subcontainer count or zero if not supported;
   */
  int getSubcontainerCount();

  /**
   * Returns the subcontainer at the selected index. If the container doesn't exist or is invalid,
   * an empty result is returned. Legacy code always returns empty.
   *
   * @param index the index in the container to return
   * @param auxData any Auxiliary data to append to the subcontainer code. If fetching an initcode
   *     container, pass null.
   * @param evm the EVM in which we are instantiating the code
   * @return Either the subcontainer, or empty.
   */
  Optional<Code> getSubContainer(final int index, final Bytes auxData, EVM evm);

  /**
   * Loads data from the appropriate data section
   *
   * @param offset Where within the data section to start copying
   * @param length how many bytes to copy
   * @return A slice of the code containing the requested data
   */
  Bytes getData(final int offset, final int length);

  /**
   * Read a signed 16-bit big-endian integer
   *
   * @param startIndex the index to start reading the integer in the code
   * @return a java int representing the 16-bit signed integer.
   */
  int readBigEndianI16(final int startIndex);

  /**
   * Read an unsigned 16 bit big-endian integer
   *
   * @param startIndex the index to start reading the integer in the code
   * @return a java int representing the 16-bit unsigned integer.
   */
  int readBigEndianU16(final int startIndex);

  /**
   * Read an unsigned 8-bit integer
   *
   * @param startIndex the index to start reading the integer in the code
   * @return a java int representing the 8-bit unsigned integer.
   */
  int readU8(final int startIndex);

  /**
   * A more readable representation of the hex bytes, including whitespace and comments after hashes
   *
   * @return The pretty printed code
   */
  String prettyPrint();
}
