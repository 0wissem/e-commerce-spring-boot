#!/usr/bin/env bash
# Verifies that every order item returned by the API has a valid product_snapshot
# after the V18/V19 migration chain.
#
# Usage:
#   ./scripts/verify-snapshots.sh                        # defaults to localhost:8080
#   ./scripts/verify-snapshots.sh http://my-env.com      # against any environment

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
PAGES=5   # how many pages to sample (page size = 20 each → 100 orders checked)

command -v jq >/dev/null 2>&1 || { echo "jq is required (brew install jq)"; exit 1; }

check_order() {
  local order_id="$1"
  local items_json="$2"
  local item_count
  item_count=$(echo "$items_json" | jq 'length')

  if [ "$item_count" -eq 0 ]; then
    return
  fi

  for i in $(seq 0 $((item_count - 1))); do
    local item
    item=$(echo "$items_json" | jq ".[$i]")

    local snapshot
    snapshot=$(echo "$item" | jq '.productSnapshot')

    if [ "$snapshot" = "null" ]; then
      echo "  FAIL  order $order_id — item $i has null productSnapshot"
      ((FAIL++))
      continue
    fi

    local name price categories
    name=$(echo "$snapshot" | jq -r '.name')
    price=$(echo "$snapshot" | jq '.price')
    categories=$(echo "$snapshot" | jq '.categories')

    if [ -z "$name" ] || [ "$name" = "null" ]; then
      echo "  FAIL  order $order_id — item $i snapshot missing 'name'"
      ((FAIL++)); continue
    fi

    if [ -z "$price" ] || [ "$price" = "null" ]; then
      echo "  FAIL  order $order_id — item $i snapshot missing 'price'"
      ((FAIL++)); continue
    fi

    if [ "$categories" = "null" ]; then
      echo "  FAIL  order $order_id — item $i snapshot missing 'categories'"
      ((FAIL++)); continue
    fi

    local cat_count
    cat_count=$(echo "$categories" | jq 'length')
    if [ "$cat_count" -eq 0 ]; then
      echo "  FAIL  order $order_id — item $i snapshot has empty categories array"
      ((FAIL++)); continue
    fi

    # Verify each category has id + name
    local bad_cats
    bad_cats=$(echo "$categories" | jq '[.[] | select(.id == null or .name == null)] | length')
    if [ "$bad_cats" -gt 0 ]; then
      echo "  FAIL  order $order_id — item $i has $bad_cats category object(s) missing id/name"
      ((FAIL++)); continue
    fi

    ((PASS++))
  done
}

echo "Verifying snapshots against $BASE_URL"
echo "Sampling $PAGES pages × 20 orders..."
echo ""

for page in $(seq 0 $((PAGES - 1))); do
  response=$(curl -sf "$BASE_URL/api/orders?page=$page&size=20" \
    -H "Accept: application/json") || {
    echo "ERROR: Could not reach $BASE_URL/api/orders?page=$page"
    exit 1
  }

  orders=$(echo "$response" | jq '.data.content')
  order_count=$(echo "$orders" | jq 'length')

  echo "Page $page — $order_count orders"

  for i in $(seq 0 $((order_count - 1))); do
    order_id=$(echo "$orders" | jq -r ".[$i].id")
    items=$(echo "$orders" | jq ".[$i].items")
    check_order "$order_id" "$items"
  done
done

echo ""
echo "────────────────────────────"
echo "  PASS: $PASS order items"
echo "  FAIL: $FAIL order items"
echo "────────────────────────────"

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
