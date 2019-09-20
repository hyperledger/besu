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
package org.hyperledger.besu.consensus.ibft.protocol;

import org.hyperledger.besu.consensus.ibft.messagedata.IbftV2;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

public class IbftSubProtocol implements SubProtocol {

  public static String NAME = "IBF";
  public static final Capability IBFV1 = Capability.create(NAME, 1);

  private static final IbftSubProtocol INSTANCE = new IbftSubProtocol();

  public static IbftSubProtocol get() {
    return INSTANCE;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int messageSpace(final int protocolVersion) {
    return IbftV2.MESSAGE_SPACE;
  }

  @Override
  public boolean isValidMessageCode(final int protocolVersion, final int code) {
    switch (code) {
      case IbftV2.PROPOSAL:
      case IbftV2.PREPARE:
      case IbftV2.COMMIT:
      case IbftV2.ROUND_CHANGE:
        return true;

      default:
        return false;
    }
  }

  @Override
  public String messageName(final int protocolVersion, final int code) {
    switch (code) {
      case IbftV2.PROPOSAL:
        return "Proposal";
      case IbftV2.PREPARE:
        return "Prepare";
      case IbftV2.COMMIT:
        return "Commit";
      case IbftV2.ROUND_CHANGE:
        return "RoundChange";
      default:
        return INVALID_MESSAGE_NAME;
    }
  }
}
