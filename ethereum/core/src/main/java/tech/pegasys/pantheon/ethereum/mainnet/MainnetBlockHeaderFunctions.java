/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.ParsedExtraData;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.util.bytes.BytesValue;

/** Implements the block hashing algorithm for MainNet as per the yellow paper. */
public class MainnetBlockHeaderFunctions implements BlockHeaderFunctions {

  @Override
  public Hash hash(final BlockHeader header) {
    return createHash(header);
  }

  public static Hash createHash(final BlockHeader header) {
    final BytesValue rlp = RLP.encode(header::writeTo);
    return Hash.hash(rlp);
  }

  @Override
  public ParsedExtraData parseExtraData(final BlockHeader header) {
    return null;
  }
}
