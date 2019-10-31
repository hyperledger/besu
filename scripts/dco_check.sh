#!/bin/bash

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
