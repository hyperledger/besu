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
package tech.pegasys.pantheon.plugins.services;

public interface PantheonEvents {

  /**
   * Returns the raw RLP of a block that Pantheon has received and that has passed basic validation
   * checks.
   *
   * @param blockJSONListener The listener that will accept a JSON string as the event.
   * @return an object to be used as an identifier when de-registering the event.
   */
  Object addNewBlockPropagatedListener(NewBlockPropagatedListener blockJSONListener);

  /**
   * Remove the blockAdded listener from pantheon notifications.
   *
   * @param listenerIdentifier The instance that was returned from addBlockAddedListener;
   */
  void removeNewBlockPropagatedListener(Object listenerIdentifier);

  interface NewBlockPropagatedListener {
    void newBlockPropagated(String jsonBlock);
  }
}
