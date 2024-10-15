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

status=0
while IFS= read -r -a line; do
  my_array+=( "$line" )
  done < <( git branch -r | grep -v origin/HEAD )
for branch in "${my_array[@]}"
do
  branch=$(echo "$branch" | xargs)
  echo "Checking commits in branch $branch for commits missing DCO..."
  while read -r results; do
    status=1
    commit_hash="$(echo "$results" | cut -d' ' -f1)"
    >&2 echo "$commit_hash is missing Signed-off-by line."
  done < <(git log "$branch" --no-merges --pretty="%H %ae" --grep 'Signed-off-by' --invert-grep -- )
done

exit $status
