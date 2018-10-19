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
package tech.pegasys.pantheon.consensus.ibftlegacy.protocol;

import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV63;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;

import java.util.Arrays;
import java.util.List;

/**
 * Represents the istanbul/64 protocol as used by Quorum (effectively an extension of eth/63, which
 * adds a single message type (0x11) to encapsulate all communications required for IBFT block
 * mining.
 */
public class Istanbul64Protocol implements SubProtocol {

  private static final String NAME = "istanbul";
  private static final int VERSION = 64;

  static final Capability ISTANBUL64 = Capability.create(NAME, 64);
  static final int INSTANBUL_MSG = 0x11;

  private static final Istanbul64Protocol INSTANCE = new Istanbul64Protocol();

  private static final List<Integer> istanbul64Messages =
      Arrays.asList(
          EthPV62.STATUS,
          EthPV62.NEW_BLOCK_HASHES,
          EthPV62.TRANSACTIONS,
          EthPV62.GET_BLOCK_HEADERS,
          EthPV62.BLOCK_HEADERS,
          EthPV62.GET_BLOCK_BODIES,
          EthPV62.BLOCK_BODIES,
          EthPV62.NEW_BLOCK,
          EthPV63.GET_NODE_DATA,
          EthPV63.NODE_DATA,
          EthPV63.GET_RECEIPTS,
          EthPV63.RECEIPTS,
          INSTANBUL_MSG);

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int messageSpace(final int protocolVersion) {
    return INSTANBUL_MSG + 1;
  }

  @Override
  public boolean isValidMessageCode(final int protocolVersion, final int code) {
    if (protocolVersion == VERSION) {
      return istanbul64Messages.contains(code);
    }
    return false;
  }

  public static Istanbul64Protocol get() {
    return INSTANCE;
  }
}
