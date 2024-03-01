N=$2 # Number of groups
i=0 # Initialize counter
cat $1 | while read line; do
  echo "$line" >> "group_$((i % N + 1)).txt"
  let i++
done

