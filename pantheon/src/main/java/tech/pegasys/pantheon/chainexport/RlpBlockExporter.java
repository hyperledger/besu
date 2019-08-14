/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.chainexport;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.FileOutputStream;
import java.io.IOException;

public class RlpBlockExporter extends BlockExporter {

  public RlpBlockExporter(final Blockchain blockchain) {
    super(blockchain);
  }

  @Override
  protected void exportBlock(final FileOutputStream outputStream, final Block block)
      throws IOException {
    final BytesValue rlp = RLP.encode(block::writeTo);
    outputStream.write(rlp.getArrayUnsafe());
  }
}
