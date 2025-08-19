/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.util.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/** Factory for creating OutputStreams */
public class OutputStreamFactory {

  /** Constructs an OutputStreamFactory */
  public OutputStreamFactory() {}

  /**
   * Create a FileOutputStream to write to the specified file
   *
   * @param file The file to open a FileOutputStream for
   * @return a FileOutputStream to write to the specified file
   * @throws FileNotFoundException File not found exception
   */
  public FileOutputStream createFileOutputStream(final File file) throws FileNotFoundException {
    return new FileOutputStream(file);
  }
}
