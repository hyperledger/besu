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
package org.hyperledger.besu.datatypes;

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes48;

/** This class contains the data for a KZG cell proof for a KZG commitment. */
public class KZGCellProof {
  final Bytes48 data;

  /**
   * Constructor for a KZG cell proof.
   *
   * @param data The data for the KZG cell proof.
   */
  public KZGCellProof(final Bytes48 data) {
    this.data = data;
  }

  /**
   * Reads a KZG cell proof from the RLP input.
   *
   * @param input The RLP input.
   * @return The KZG cell proof.
   */
  public static KZGCellProof readFrom(final RLPInput input) {
    final Bytes48 bytes = input.readBytes48();
    return new KZGCellProof(bytes);
  }

  /**
   * Writes the KZG cell proof to the RLP output.
   *
   * @param out The RLP output.
   */
  public void writeTo(final RLPOutput out) {
    out.writeBytes(data);
  }

  /**
   * Gets the data for the KZG cell proof.
   *
   * @return The data for the KZG cell proof.
   */
  public Bytes48 getData() {
    return data;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    KZGCellProof kzgProof = (KZGCellProof) o;
    return Objects.equals(getData(), kzgProof.getData());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getData());
  }
}
