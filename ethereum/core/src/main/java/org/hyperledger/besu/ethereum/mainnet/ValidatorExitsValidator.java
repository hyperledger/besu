/*
 * Copyright contributors to Hyperledger Besu
 *
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

package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.ValidatorExit;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ValidatorExitsValidator {

  boolean validateValidatorExitParameter(Optional<List<ValidatorExit>> validatorExits);

  boolean validateExitsInBlock(Block block, List<ValidatorExit> validatorExits);

  /** Used before Prague */
  class ProhibitedExits implements ValidatorExitsValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ProhibitedExits.class);

    /**
     * Before Prague we do not expect to have execution layer triggered exits, so it is expected the
     * optional parameter will be empty
     *
     * @param validatorExits Optional list of exits
     * @return true, if valid, false otherwise
     */
    @Override
    public boolean validateValidatorExitParameter(
        final Optional<List<ValidatorExit>> validatorExits) {
      return validatorExits.isEmpty();
    }

    @Override
    public boolean validateExitsInBlock(
        final Block block, final List<ValidatorExit> validatorExits) {
      final Optional<List<ValidatorExit>> maybeExits = block.getBody().getExits();
      if (maybeExits.isPresent()) {
        LOG.warn("Block {} contains exits but exits are prohibited", block.getHash());
        return false;
      }

      if (block.getHeader().getExitsRoot().isPresent()) {
        LOG.warn("Block {} header contains exits_root but exits are prohibited", block.getHash());
        return false;
      }

      return true;
    }
  }

  /** Used after Prague */
  class AllowedExits implements ValidatorExitsValidator {

    private static final Logger LOG = LoggerFactory.getLogger(AllowedExits.class);

    @Override
    public boolean validateValidatorExitParameter(
        final Optional<List<ValidatorExit>> validatorExits) {
      return validatorExits.isPresent();
    }

    @Override
    public boolean validateExitsInBlock(
        final Block block, final List<ValidatorExit> expectedExits) {
      final Hash blockHash = block.getHash();

      if (block.getHeader().getExitsRoot().isEmpty()) {
        LOG.warn("Block {} must contain exits_root", blockHash);
        return false;
      }

      if (block.getBody().getExits().isEmpty()) {
        LOG.warn("Block {} must contain exits (even if empty list)", blockHash);
        return false;
      }

      final List<ValidatorExit> exitsInBlock = block.getBody().getExits().get();
      // TODO Do we need to allow for customization? (e.g. if the value changes in the next fork)
      if (exitsInBlock.size() > ValidatorExitContractHelper.MAX_EXITS_PER_BLOCK) {
        LOG.warn("Block {} has more than the allowed maximum number of exits", blockHash);
        return false;
      }

      // Validate exits_root
      final Hash expectedExitsRoot = BodyValidation.exitsRoot(exitsInBlock);
      if (!expectedExitsRoot.equals(block.getHeader().getExitsRoot().get())) {
        LOG.warn(
            "Block {} exits_root does not match expected hash root for exits in block", blockHash);
        return false;
      }

      // Validate exits
      final boolean expectedExitsMatch = expectedExits.equals(exitsInBlock);
      if (!expectedExitsMatch) {
        LOG.warn(
            "Block {} has a mismatch between its exits and expected exits (in_block = {}, expected = {})",
            blockHash,
            exitsInBlock,
            expectedExits);
        return false;
      }

      return true;
    }
  }
}
