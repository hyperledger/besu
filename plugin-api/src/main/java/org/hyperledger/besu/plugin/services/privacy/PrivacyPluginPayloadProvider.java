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
package org.hyperledger.besu.plugin.services.privacy;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.data.PrivateTransaction;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * Allows you to register a provider that will dictate how the payload of a privacy marker
 * transaction is handled.
 */
@Deprecated(since = "24.12.0")
public interface PrivacyPluginPayloadProvider {

  /**
   * Given a private transaction what information should be stored on chain in the payload of the
   * privacy marker transaction? At it's simplest you could just serialize the privateTransaction
   * using RLP. This method is called when the user has submitted a private transaction using
   * `eea_sendRawTransaction`. If you do not know how to serialize the PrivateTransaction you should
   * throw an exception so that an error is returned to the user.
   *
   * @param privateTransaction the initial private transaction
   * @param privacyUserId the user id - only used in multi-tenant environments.
   * @return the raw bytes of what should be stored in {@link Transaction#getPayload()}
   */
  Bytes generateMarkerPayload(PrivateTransaction privateTransaction, String privacyUserId);

  /**
   * When processing privacy marker transactions besu needs to know how to process them. The plugin
   * must perform the deserialization steps required. If the node shouldn't have access to that
   * transaction return Optional.empty().<br>
   * <br>
   * Note! Any exceptions thrown in this method with cause besu to crash and stop processing blocks
   *
   * @param transaction the privacy marker transaction
   * @return The PrivateTransaction to apply to private world state.
   */
  Optional<PrivateTransaction> getPrivateTransactionFromPayload(Transaction transaction);
}
