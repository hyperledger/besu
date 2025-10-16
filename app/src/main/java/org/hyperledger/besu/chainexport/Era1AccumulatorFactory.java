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
package org.hyperledger.besu.chainexport;

import org.hyperledger.besu.util.ssz.Merkleizer;

/** A factory for producing Era1Accumulator objects */
public class Era1AccumulatorFactory {

  /** Default constructor */
  public Era1AccumulatorFactory() {}

  /**
   * Creates an Era1Accumulator object with the default Merkleizer
   *
   * @return an Era1Accumulator object
   */
  public Era1Accumulator getEra1Accumulator() {
    return new Era1Accumulator(new Merkleizer());
  }
}
