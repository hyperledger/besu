#!/bin/bash

REPORTS_DIR="$1"
SPLIT_COUNT=$2
SPLIT_INDEX=$3

# extract tests time from Junit XML reports
find "$REPORTS_DIR" -type f -name TEST-*.xml | xargs -I{} bash -c "xmlstarlet sel -t -v 'sum(//testcase/@time)' '{}'; echo '{}' | sed 's/.*TEST\-\(.*\)\.xml/ \1/'" > tmp/timing.tsv

# Sort times in descending order
IFS=$'\n' sorted=($(sort -nr tmp/timing.tsv))
unset IFS

sums=()
tests=()

# Initialize sums
for ((i=0; i<SPLIT_COUNT; i++))
do
	sums[$i]=0
done

# add tests to groups trying to balance the sum of execution time of each group
for line in "${sorted[@]}"; do
	line_parts=( $line )
	test_time=${line_parts[0]//./} # convert to millis
	test_time=${test_time##0} # remove leading zeros
	test_name=${line_parts[1]}

  # Does the test still exists?
  if grep -F -q --line-regexp "$test_name" tmp/currentTests.list
  then
    # Find index of min sum
    idx_min_sum=0
    min_sum=${sums[0]}
    for ((i=0; i<SPLIT_COUNT; i++))
    do
      if [[ ${sums[$i]} -lt $min_sum ]]
      then
        idx_min_sum=$i
        min_sum=${sums[$i]}
      fi
    done

    # Add the test to the min sum list
    min_sum_tests=${tests[$idx_min_sum]}
    tests[$idx_min_sum]="${min_sum_tests}${test_name},"

    # Update the sums
    ((sums[idx_min_sum]+=test_time))

    echo "$test_name" >> tmp/processedTests.list
  fi
done

# Any new test?
grep -F --line-regexp -v -f tmp/processedTests.list tmp/currentTests.list > tmp/newTests.list
idx_new_test=0
while read -r new_test_name
do
  idx_group=$(( idx_new_test % SPLIT_COUNT ))
  group=${tests[$idx_group]}
  tests[$idx_group]="${group}${new_test_name},"
  idx_new_test=$(( idx_new_test + 1 ))
done < tmp/newTests.list


# return the requests index, without quotes to drop the last trailing space
echo ${tests[$SPLIT_INDEX]//,/ }