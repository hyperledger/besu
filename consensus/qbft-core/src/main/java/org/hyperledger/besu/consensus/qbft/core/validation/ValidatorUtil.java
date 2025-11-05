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
package org.hyperledger.besu.consensus.qbft.core.validation;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.datatypes.Address;

import java.util.Collection;

/** Utility class for validating QBFT messages. */
public class ValidatorUtil {

  /** Default constructor. */
  private ValidatorUtil() {
    // Utility class
  }

  /**
   * Check if a message is from a known validator.
   *
   * @param msg the BFT message
   * @param validators the collection of validator addresses
   * @return true if the message author is a known validator
   */
  public static boolean isMsgFromKnownValidator(
      final BftMessage<?> msg, final Collection<Address> validators) {
    return validators.contains(msg.getAuthor());
  }

  /**
   * Check if a message is for the current chain height.
   *
   * @param bftMessage the BFT message
   * @param currentChainHeight the current chain height
   * @return true if the message is for the current chain height
   */
  public static boolean isMsgForCurrentHeight(
      final BftMessage<?> bftMessage, final long currentChainHeight) {
    return isMsgForCurrentHeight(bftMessage.getRoundIdentifier(), currentChainHeight);
  }

  /**
   * Check if a round identifier is for the current chain height.
   *
   * @param roundIdentifier the consensus round identifier
   * @param currentChainHeight the current chain height
   * @return true if the round identifier is for the current chain height
   */
  public static boolean isMsgForCurrentHeight(
      final ConsensusRoundIdentifier roundIdentifier, final long currentChainHeight) {
    return roundIdentifier.getSequenceNumber() == currentChainHeight;
  }

  /**
   * Check if a message is for a future chain height.
   *
   * @param bftMessage the BFT message
   * @param currentChainHeight the current chain height
   * @return true if the message is for a future chain height
   */
  public static boolean isMsgForFutureChainHeight(
      final BftMessage<?> bftMessage, final long currentChainHeight) {
    return bftMessage.getRoundIdentifier().getSequenceNumber() > currentChainHeight;
  }
}
