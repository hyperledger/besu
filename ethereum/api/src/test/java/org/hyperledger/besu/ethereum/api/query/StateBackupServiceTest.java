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
 *
 */

package org.hyperledger.besu.ethereum.api.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.Test;

public class StateBackupServiceTest {

  private final Path backupDir = Path.of("/tmp/backup");

  @Test
  public void fileIndexRenames() {
    assertThat(StateBackupService.dataFileToIndex(Path.of("/tmp/besu-blocks-0000.cdat")).toString())
        .isEqualTo("/tmp/besu-blocks.cidx");
    assertThat(StateBackupService.dataFileToIndex(Path.of("/tmp/besu.blocks.0000.rdat")).toString())
        .isEqualTo("/tmp/besu.blocks.ridx");
  }

  @Test
  public void leafFileName() {
    assertThat(StateBackupService.accountFileName(backupDir, 4_000_000, 42, false).toString())
        .isEqualTo("/tmp/backup/besu-account-backup-04000000-0042.rdat");
    assertThat(StateBackupService.accountFileName(backupDir, 6_000_000, 46, true).toString())
        .isEqualTo("/tmp/backup/besu-account-backup-06000000-0046.cdat");
  }

  @Test
  public void headerFileName() {
    assertThat(StateBackupService.headerFileName(backupDir, 42, false).toString())
        .isEqualTo("/tmp/backup/besu-header-backup-0042.rdat");
    assertThat(StateBackupService.headerFileName(backupDir, 46, true).toString())
        .isEqualTo("/tmp/backup/besu-header-backup-0046.cdat");
  }

  @Test
  public void bodyFileName() {
    assertThat(StateBackupService.bodyFileName(backupDir, 42, false).toString())
        .isEqualTo("/tmp/backup/besu-body-backup-0042.rdat");
    assertThat(StateBackupService.bodyFileName(backupDir, 46, true).toString())
        .isEqualTo("/tmp/backup/besu-body-backup-0046.cdat");
  }

  @Test
  public void receiptFileName() {
    assertThat(StateBackupService.receiptFileName(backupDir, 42, false).toString())
        .isEqualTo("/tmp/backup/besu-receipt-backup-0042.rdat");
    assertThat(StateBackupService.receiptFileName(backupDir, 46, true).toString())
        .isEqualTo("/tmp/backup/besu-receipt-backup-0046.cdat");
  }
}
