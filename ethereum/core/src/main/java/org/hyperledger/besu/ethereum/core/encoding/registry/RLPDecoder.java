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
package org.hyperledger.besu.ethereum.core.encoding.registry;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

public interface RLPDecoder<T> {
  default T readFrom(final RLPInput input) {
    throw new UnsupportedOperationException("Implementations must provide a hash function.");
  }

  default T readFrom(final RLPInput input, final BlockHeaderFunctions hashFunction) {
    throw new UnsupportedOperationException("This decoder does not require a hash function.");
  }

  default T readFrom(final Bytes bytes) {
    return readFrom(RLP.input(bytes));
  }
}
