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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.Account;

import org.apache.tuweni.bytes.Bytes32;

public class ChainHeadPrivateNonceProvider implements PrivateNonceProvider {
  private static final Logger LOG = LogManager.getLogger();
  private final Blockchain blockchain;
  private final PrivateStateRootResolver privateStateRootResolver;
  private final WorldStateArchive privateWorldStateArchive;
  private final PmtTransactionPool pmtTransactionPool;

  public ChainHeadPrivateNonceProvider(
      final Blockchain blockchain,
      final PrivateStateRootResolver privateStateRootResolver,
      final WorldStateArchive privateWorldStateArchive,
      final PmtTransactionPool pmtTransactionPool) {
    this.blockchain = blockchain;
    this.privateStateRootResolver = privateStateRootResolver;
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.pmtTransactionPool = pmtTransactionPool;
  }


  /**
   * Calculate the nonce while taking into account any PMTs that are already in progress. This makes
   * nonce management for private tx slightly cleaner.
   *
   * @param sender the sender of the transaction
   * @param privacyGroupId the privacy group ID this tx is for
   * @return the nonce, taking into account the PMT in the pool
   */
  @Override
  public long getNonce(final Address sender, final Bytes32 privacyGroupId) {
    final BlockHeader chainHeadHeader = blockchain.getChainHeadHeader();
    final Hash chainHeadHash = chainHeadHeader.getHash();

    LOG.info(
        "checking for PMT matches for sender " + sender + " and privacyGroupID (base 64) " + privacyGroupId.toBase64String());
    // TODO get pendingNonce
    // if latestNonce > pendingNonce return latestNonce (as below)
    // else return pendingNonce + 1
    long pendingPrivateNonceFromPmtPool = pmtTransactionPool.getMaxMatchingNonce(sender.toHexString(), privacyGroupId.toBase64String());

    final Hash stateRoot =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, chainHeadHash);
    long stateBasedPrivateNonce = privateWorldStateArchive
        .get(stateRoot, chainHeadHash)
        .map(
            privateWorldState -> {
              final Account account = privateWorldState.get(sender);
              return account == null ? 0L : account.getNonce();
            })
        .orElse(Account.DEFAULT_NONCE);

    if (pendingPrivateNonceFromPmtPool == 0 && stateBasedPrivateNonce == 0) {
      return 0L;
    }
    if (pendingPrivateNonceFromPmtPool >= stateBasedPrivateNonce) {
      LOG.info("using pending nonce {}", pendingPrivateNonceFromPmtPool + 1);
      return pendingPrivateNonceFromPmtPool + 1 ; // TODO is +1 right here?
    } else {
      LOG.info("using state based nonce {} ", stateBasedPrivateNonce);
      return stateBasedPrivateNonce;
    }
  }
}
