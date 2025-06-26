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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.ethereum.core.BlockAccessList;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public final class BlockAccessListEncoder {

  private BlockAccessListEncoder() {}

  public static void encode(final BlockAccessList bal, final RLPOutput out) {
    out.writeList(
        bal.getAccountChanges(),
        (acct, acctOut) -> {
          acctOut.startList();
          acctOut.writeBytes(acct.getAddress());

          acctOut.writeList(
              acct.getStorageChanges(),
              (sc, scOut) -> {
                scOut.startList();
                scOut.writeUInt256Scalar(sc.getSlot().getSlotKey().get());
                scOut.writeList(
                    sc.getChanges(),
                    (chg, chgOut) -> {
                      chgOut.startList();
                      chgOut.writeInt(chg.getTxIndex());
                      chgOut.writeBytes(chg.getNewValue());
                      chgOut.endList();
                    });
                scOut.endList();
              });

          acctOut.writeList(
              acct.getStorageReads(),
              (sr, srOut) -> srOut.writeUInt256Scalar(sr.getSlot().getSlotKey().get()));

          acctOut.writeList(
              acct.getBalanceChanges(),
              (bc, bcOut) -> {
                bcOut.startList();
                bcOut.writeInt(bc.getTxIndex());
                bcOut.writeBytes(bc.getPostBalance());
                bcOut.endList();
              });

          acctOut.writeList(
              acct.getNonceChanges(),
              (nc, ncOut) -> {
                ncOut.startList();
                ncOut.writeInt(nc.getTxIndex());
                ncOut.writeLongScalar(nc.getNewNonce());
                ncOut.endList();
              });

          acctOut.writeList(
              acct.getCodeChanges(),
              (cc, ccOut) -> {
                ccOut.startList();
                ccOut.writeInt(cc.getTxIndex());
                ccOut.writeBytes(cc.getNewCode());
                ccOut.endList();
              });

          acctOut.endList();
        });
  }
}
