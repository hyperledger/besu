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
 *
 * SPDX-License-Identifier: Apache-2.0
 */



import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.BuildException

class CheckSpdxHeader extends DefaultTask {
  private String rootPath
  private String spdxHeader
  private String filesRegex
  private String excludeRegex

  @Input
  String getRootPath() {
    return rootPath
  }

  void setRootPath(final String rootPath) {
    this.rootPath = rootPath
  }

  @Input
  String getSpdxHeader() {
    return spdxHeader
  }

  void setSpdxHeader(final String spdxHeader) {
    this.spdxHeader = spdxHeader
  }

  @Input
  String getFilesRegex() {
    return filesRegex
  }

  void setFilesRegex(final String filesRegex) {
    this.filesRegex = filesRegex
  }

  @Input
  String getExcludeRegex() {
    return excludeRegex
  }

  void setExcludeRegex(final String excludeRegex) {
    this.excludeRegex = excludeRegex
  }

  @TaskAction
  void checkHeaders() {
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

    if (!filesWithoutHeader.isEmpty()) {
      throw new BuildException("Files without headers: " + filesWithoutHeader.join('\n'), null)
    }
  }

}