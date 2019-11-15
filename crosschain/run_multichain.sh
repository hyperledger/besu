#!/bin/bash

# This script partitions the terminal window using tmux,
# running as many instances of CMD_LINE as params are in PARAMS,
# plus an extra shell

CMD_LINE="crosschain/run_node.js "
PARAMS="33 22 11"

STANDOUT=`tput smso`
OFFSTANDOUT=`tput rmso`
MESSAGE="Press $STANDOUT Control-Q $OFFSTANDOUT to exit everything"

tmux new-session -s multichain -d "bash --init-file <(echo \". \"$HOME/.bash_profile\"; echo $MESSAGE\" ) "
# The init-file incantation is needed so bash stays interactive after running the given command
# The initial pane is not special in any way. It simply feels comfortable to have a shell there.
tmux bind-key -n C-q kill-session
tmux set remain-on-exit on
tmux set -g mouse on

#tmux set -g display-time 0
#tmux display-message "Press Control-Q to exit everything"

for CHAINID in $PARAMS
do
    FULL_CMD_LINE="$CMD_LINE $CHAINID"
    tmux split-window -v -d "bash -c \"echo $STANDOUT Running '$FULL_CMD_LINE' $OFFSTANDOUT ; $FULL_CMD_LINE\""
done
tmux select-layout even-vertical
tmux attach
