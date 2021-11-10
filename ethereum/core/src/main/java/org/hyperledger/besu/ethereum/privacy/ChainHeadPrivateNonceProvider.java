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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.Account;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

public class ChainHeadPrivateNonceProvider implements PrivateNonceProvider {
  private static final Logger LOG = LogManager.getLogger();
  private final Blockchain blockchain;
  private final PrivateStateRootResolver privateStateRootResolver;
  private final WorldStateArchive privateWorldStateArchive;
  private final PrivateMarkerTransactionPool privateMarkerTransactionPool;

  public ChainHeadPrivateNonceProvider(
      final Blockchain blockchain,
      final PrivateStateRootResolver privateStateRootResolver,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateMarkerTransactionPool privateMarkerTransactionPool) {
    this.blockchain = blockchain;
    this.privateStateRootResolver = privateStateRootResolver;
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.privateMarkerTransactionPool = privateMarkerTransactionPool;
  }

  /**
   * Calculate the nonce, while taking into account any PMTs that are already in progress. This
   * reduces the window where successive private transactions would get the same nonce based on
   * priv_getTransactionCount.
   *
   * @param sender the sender of the transaction
   * @param privacyGroupId the privacy group ID this tx is for
   * @return the nonce, taking into account any PMT in the pool
   */
  @Override
  public long getNonce(final Address sender, final Bytes32 privacyGroupId) {
    final BlockHeader chainHeadHeader = blockchain.getChainHeadHeader();
    final Hash chainHeadHash = chainHeadHeader.getHash();

    LOG.info(
        "checking for PMT matches for sender "
            + sender
            + " and privacyGroupID (base 64) "
            + privacyGroupId.toBase64String());
    final Optional<Long> maybePendingPrivateNonceFromPmtPool =
        privateMarkerTransactionPool.getMaxMatchingNonce(
            sender.toHexString(), privacyGroupId.toBase64String());

    final Hash stateRoot =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, chainHeadHash);
    long stateBasedPrivateNonce =
        privateWorldStateArchive
            .get(stateRoot, chainHeadHash)
            .map(
                privateWorldState -> {
                  final Account account = privateWorldState.get(sender);
                  return account == null ? 0L : account.getNonce();
                })
            .orElse(Account.DEFAULT_NONCE);

    return (maybePendingPrivateNonceFromPmtPool
        .map(
            pending -> {
              return (pending >= stateBasedPrivateNonce ? pending + 1 : stateBasedPrivateNonce);
            })
        .orElse(stateBasedPrivateNonce));
  }
}
