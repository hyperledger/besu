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
package org.hyperledger.besu.crosschain.protocol;

import org.hyperledger.besu.crosschain.messagedata.CrosschainMessageCodes;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

public class CrosschainSubProtocol implements SubProtocol {

  public static String NAME = "CCH";
  public static final Capability CCH = Capability.create(NAME, 1);

  private static final CrosschainSubProtocol INSTANCE = new CrosschainSubProtocol();

  public static CrosschainSubProtocol get() {
    return INSTANCE;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int messageSpace(final int protocolVersion) {
    return CrosschainMessageCodes.MESSAGE_SPACE;
  }

  @Override
  public boolean isValidMessageCode(final int protocolVersion, final int code) {
    switch (code) {
      case CrosschainMessageCodes.PING:
      case CrosschainMessageCodes.PONG:
        return true;

      default:
        return false;
    }
  }

  @Override
  public String messageName(final int protocolVersion, final int code) {
    switch (code) {
      case CrosschainMessageCodes.PING:
        return "Ping";
      case CrosschainMessageCodes.PONG:
        return "Pong";
      default:
        return INVALID_MESSAGE_NAME;
    }
  }
}
