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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public abstract class BftExtraDataCodec {

  protected enum EncodingType {
    ALL,
    EXCLUDE_COMMIT_SEALS,
    EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER,
    WITHOUT_CMS // TODO-lucas How can we achieve this w/o changing the BftExtraDataCodec base class
  }

  private static final Logger LOG = LogManager.getLogger();

  public static int EXTRA_VANITY_LENGTH = 32;

  public Bytes encode(final BftExtraData bftExtraData) {
    return encode(bftExtraData, EncodingType.ALL);
  }

  public Bytes encodeWithoutCommitSeals(final BftExtraData bftExtraData) {
    return encode(bftExtraData, EncodingType.EXCLUDE_COMMIT_SEALS);
  }

  public Bytes encodeWithoutCommitSealsAndRoundNumber(final BftExtraData bftExtraData) {
    return encode(bftExtraData, EncodingType.EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER);
  }

  public Bytes encodeForProposal(final BftExtraData bftExtraData) {
    return encode(bftExtraData, EncodingType.WITHOUT_CMS);
  }

  protected abstract Bytes encode(final BftExtraData bftExtraData, final EncodingType encodingType);

  public BftExtraData decode(final BlockHeader blockHeader) {
    final Object inputExtraData = blockHeader.getParsedExtraData();
    if (inputExtraData instanceof BftExtraData) {
      return (BftExtraData) inputExtraData;
    }
    LOG.warn(
        "Expected a BftExtraData instance but got {}. Reparsing required.",
        inputExtraData != null ? inputExtraData.getClass().getName() : "null");
    return decodeRaw(blockHeader.getExtraData());
  }

  public abstract BftExtraData decodeRaw(Bytes bytes);
}
