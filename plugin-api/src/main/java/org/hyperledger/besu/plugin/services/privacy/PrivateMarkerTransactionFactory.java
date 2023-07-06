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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.plugin.data.PrivateTransaction;
import org.hyperledger.besu.plugin.data.UnsignedPrivateMarkerTransaction;

import org.apache.tuweni.bytes.Bytes;

/** Implement an instance of this for signing and serializing privacy marker transactions. */
public interface PrivateMarkerTransactionFactory {

  /**
   * We need this from the plugin so that we can provide the correct nonce in {@link
   * #create(UnsignedPrivateMarkerTransaction, PrivateTransaction, String)}
   *
   * @param privateTransaction the transaction about to be submitted to the transaction pool
   * @param privacyUserId the authz privacyUserId when in a multi-tenant environment
   * @return sender address
   */
  Address getSender(PrivateTransaction privateTransaction, String privacyUserId);

  /**
   * You need to return a raw signed transaction. This will be submitted directly into the
   * transaction pool. You can use the information provided in {@link
   * UnsignedPrivateMarkerTransaction} to sign and serialize the transaction or forge your own.
   *
   * @param unsignedPrivateMarkerTransaction the unsigned privacy marker transaction
   * @param privateTransaction the original private transaction
   * @param privacyUserId the authz privacyUserId when in a multi-tenant environment
   * @return signed raw transaction
   */
  Bytes create(
      UnsignedPrivateMarkerTransaction unsignedPrivateMarkerTransaction,
      PrivateTransaction privateTransaction,
      String privacyUserId);
}
