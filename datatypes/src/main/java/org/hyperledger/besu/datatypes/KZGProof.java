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

import org.apache.tuweni.bytes.Bytes;

/**
 * Represents a KZG proof, which contains data used in the KZG scheme. This interface defines the
 * contract for a KZG proof, which provides access to its data.
 */
public interface KZGProof {
  /**
   * Gets the data for the KZG proof.
   *
   * @return The data for the KZG proof.
   */
  Bytes getData();
}
