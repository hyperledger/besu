/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.crosschain.ethereum.crosschain;

import java.io.PrintWriter;
import java.io.StringWriter;

public class DebugHelper {
  public static String getStackTrace() {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    (new Throwable()).printStackTrace(pw);
    return sw.toString();
  }

  //    // TODO SIDECHAINS START
  //    // Debug code to dump all updated statesd for the updated world state.
  //    WorldUpdater worldUpdater = frame.getWorldState();
  //    Collection<Account> accs = worldUpdater.getTouchedAccounts();
  //    for (Account acc: accs) {
  //        LOG.info("AccUpdated1: {}, is Lockable {}",  acc.getAddress(), acc.isLockable());
  //    }
  //    // TODO SIDECHAINS END

}
