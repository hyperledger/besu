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

package org.hyperledger.besu.controller;

import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public abstract class BftBesuControllerBuilder extends BesuControllerBuilder {

  protected abstract Supplier<BftExtraDataCodec> bftExtraDataCodec();

  protected Supplier<BftBlockInterface> bftBlockInterface() {
    return Suppliers.memoize(() -> new BftBlockInterface(bftExtraDataCodec().get()));
  }
}
