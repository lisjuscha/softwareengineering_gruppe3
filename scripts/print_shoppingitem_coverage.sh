w #!/usr/bin/env bash
# Prints line coverage percentage for com.flatmanager.model.ShoppingItem from target/site/jacoco/jacoco.xml
set -euo pipefail
XML=target/site/jacoco/jacoco.xml
if [ ! -f "$XML" ]; then
  echo "Error: JaCoCo XML report not found at $XML"
  echo "Run: mvn test jacoco:report"
  exit 2
fi
# class name in JaCoCo xml uses slashes
CLASS_NAME="com/flatmanager/model/ShoppingItem"
# extract missed and covered for LINE counters
missed=$(xmllint --xpath "string(//class[@name='$CLASS_NAME']/counter[@type='LINE']/@missed)" "$XML" 2>/dev/null || echo "0")
covered=$(xmllint --xpath "string(//class[@name='$CLASS_NAME']/counter[@type='LINE']/@covered)" "$XML" 2>/dev/null || echo "0")
if [ -z "$missed" ] && [ -z "$covered" ]; then
  echo "Class $CLASS_NAME not found in $XML"
  exit 3
fi
missed=${missed:-0}
covered=${covered:-0}
total=$((missed+covered))
if [ "$total" -eq 0 ]; then
  echo "No lines for class $CLASS_NAME (total=0)"
  exit 0
fi
percent=$(awk -v c=$covered -v t=$total 'BEGIN{printf "%.1f", (c/t)*100}')
echo "ShoppingItem line coverage: ${percent}% (${covered}/${total} covered)"

