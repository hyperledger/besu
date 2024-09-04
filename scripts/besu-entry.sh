#!/bin/bash
##
## Copyright contributors to Hyperledger Besu.
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

# Function to extract the --data-path argument from the command-line arguments
# TODO : Add more checks to get data path argument passed through config file

get_data_path() {
  local NEXT_IS_PATH=false
  for i in "$@"; do
    if [ "$NEXT_IS_PATH" = true ]; then
      echo "$i"
      return
    fi
    case $i in
      --data-path=*)
        echo "${i#*=}"
        return
        ;;
      --data-path)
        NEXT_IS_PATH=true
        ;;
    esac
  done
}

# Extract the data path from the command-line arguments
DATA_PATH=$(get_data_path "$@")

# Check if the data path exists and is a directory
# TODO: add checks to make sure the path is in relevant root directories like /var or /tmp
if [ -n "$DATA_PATH" ] && [ -d "$DATA_PATH" ]; then
  echo "Data path $DATA_PATH exists. Adjusting permissions..."

  # Change ownership to besu user and group
  chown -R besu:besu $DATA_PATH

  # Ensure read/write permissions for besu user
  chmod -R u+rw $DATA_PATH

  echo "Permissions set for $DATA_PATH."
else
  echo "No valid --data-path found or the path does not exist. Proceeding without changes."
fi

# Construct the command as a single string
COMMAND="/opt/besu/bin/besu $@"

# Switch to the besu user and execute the command
exec su -s /bin/bash besu -c "$COMMAND"
