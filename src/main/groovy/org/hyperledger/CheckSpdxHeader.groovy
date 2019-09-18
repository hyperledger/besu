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

package org.hyperledger

def rootPath = System.getProperty("checkSpdxHeader.rootPath")
def spdxHeader = System.getProperty("checkSpdxHeader.spdxHeader")
def filesRegex = System.getProperty("checkSpdxHeader.filesRegex")
def excludeRegex = System.getProperty("checkSpdxHeader.excludeRegex")
def filesWithoutHeader = []

new File(rootPath).traverse(
        type: groovy.io.FileType.FILES,
        nameFilter: ~/${filesRegex}/,
        excludeFilter: ~/${excludeRegex}/
) {
    f ->
        if (!f.getText().contains(spdxHeader)) {
            filesWithoutHeader.add(f)
        }
}

if (!filesWithoutHeader.isEmpty()){
    throw new Exception("Files without headers: " + filesWithoutHeader.join('\n'))
}

