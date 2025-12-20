#!/bin/bash
##
## Copyright contributors to Besu.
##
## Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
## the License. You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
## an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
## specific language governing permissions and limitations under the License.
##
## SPDX-License-Identifier: Apache-2.0
##

REPORTS_DIR="$1"
REPORT_STRIP_PREFIX="$2"
REPORT_STRIP_SUFFIX="$3"
SPLIT_COUNT=$4
SPLIT_INDEX=$5

# extract tests time from Junit XML reports
find "$REPORTS_DIR" -type f -name TEST-*.xml | xargs -I{} bash -c "xmlstarlet sel -t -v 'concat(sum(//testcase/@time), \" \", //testsuite/testcase/@classname)' '{}'; echo '{}' | sed \"s#${REPORT_STRIP_PREFIX}/\(.*\)/${REPORT_STRIP_SUFFIX}.*# \1#\"" > tmp/timing.tsv

java .github/workflows/splitTestsByTime.java $SPLIT_COUNT $SPLIT_INDEX tmp/currentTests.list tmp/timing.tsv
