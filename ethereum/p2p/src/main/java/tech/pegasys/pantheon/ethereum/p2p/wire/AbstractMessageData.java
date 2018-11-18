/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.p2p.wire;

import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public abstract class AbstractMessageData implements MessageData {

  protected final BytesValue data;

  protected AbstractMessageData(final BytesValue data) {
    this.data = data;
  }

  @Override
  public final int getSize() {
    return data.size();
  }

  @Override
  public BytesValue getData() {
    return data;
  }
}
