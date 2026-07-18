#!/bin/bash
# F10/F14 B-m6 (real preserve booking flow: contact -> trips/left -> preserve -> order read-back).
# F10: order persists a perturbed contactsDocumentNumber (stored contact row untouched).
# F14: order persists a wrong second-class price while the batch search path shows the true price.
set -u
GW="http://localhost:8080"
EVROOT=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/cset
ADMIN_TOK=$(curl -s -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"222222"}' "$GW/api/v1/users/login" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
[ -n "$ADMIN_TOK" ] || { echo "FATAL: no admin token"; exit 1; }
DATE=$(date -d "+2 days" +%Y-%m-%d)

toggle() { curl -s -H "Authorization: Bearer $ADMIN_TOK" "$GW$1" --max-time 15 >/dev/null; }

mkuser() { local TS U PW; TS=$(date +%s%N | cut -c1-13); U="fb${1}${TS}"; PW="pass123456"
  curl -s -X POST -H "Content-Type: application/json" -d "{\"userName\":\"$U\",\"password\":\"$PW\",\"gender\":1,\"documentType\":1,\"documentNum\":\"FBD-$U\",\"email\":\"$U@corpus.test\"}" "$GW/api/v1/userservice/users/register" --max-time 20 >/dev/null
  local RESP; RESP=$(curl -s -X POST -H "Content-Type: application/json" -d "{\"username\":\"$U\",\"password\":\"$PW\"}" "$GW/api/v1/users/login" --max-time 20)
  U_TOK=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
  U_ID=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['userId'])" 2>/dev/null)
}

mkcontact() { # $1=docNum -> CID
  CID=$(curl -s -X POST -H "Authorization: Bearer $U_TOK" -H "Content-Type: application/json" -d "{\"name\":\"FB Contact\",\"accountId\":\"$U_ID\",\"documentType\":1,\"documentNumber\":\"$1\",\"phoneNumber\":\"12345678901\"}" "$GW/api/v1/contactservice/contacts" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
}

lefttrip() { # -> TRIP_ID SEARCH_PRICE (economy) via the batch search path (left clean for F14)
  local Q; Q=$(curl -s -X POST -H "Authorization: Bearer $U_TOK" -H "Content-Type: application/json" -d "{\"startPlace\":\"Shang Hai\",\"endPlace\":\"Su Zhou\",\"departureTime\":\"$DATE\"}" "$GW/api/v1/travelservice/trips/left" --max-time 30)
  TRIP_ID=$(echo "$Q" | python3 -c "import json,sys;d=json.load(sys.stdin)['data'][0];t=d['tripId'];print(t['type']+t['number'])" 2>/dev/null)
  SEARCH_PRICE=$(echo "$Q" | python3 -c "import json,sys;d=json.load(sys.stdin)['data'][0];print(d['priceForEconomyClass'])" 2>/dev/null)
}

preserve_and_read() { # -> ACK ORD_PRICE ORD_DOC ORD_ID (books SECOND class, seatType=3)
  ACK=$(curl -s -X POST -H "Authorization: Bearer $U_TOK" -H "Content-Type: application/json" -d "{\"accountId\":\"$U_ID\",\"contactsId\":\"$CID\",\"tripId\":\"$TRIP_ID\",\"seatType\":\"3\",\"date\":\"$DATE\",\"from\":\"Shang Hai\",\"to\":\"Su Zhou\",\"assurance\":\"0\",\"foodType\":0}" "$GW/api/v1/preserveservice/preserve" --max-time 40 | head -c 90)
  sleep 2
  local ROW; ROW=$(curl -s -X POST -H "Authorization: Bearer $ADMIN_TOK" -H "Content-Type: application/json" -d "{\"loginId\":\"$U_ID\",\"enableStateQuery\":false,\"enableTravelDateQuery\":false,\"enableBoughtDateQuery\":false}" "$GW/api/v1/orderservice/order/query" --max-time 20)
  ORD_PRICE=$(echo "$ROW" | python3 -c "import json,sys;d=json.load(sys.stdin)['data'];print(d[0]['price'] if d else 'NONE')" 2>/dev/null)
  ORD_DOC=$(echo "$ROW" | python3 -c "import json,sys;d=json.load(sys.stdin)['data'];print(d[0]['contactsDocumentNumber'] if d else 'NONE')" 2>/dev/null)
  ORD_ID=$(echo "$ROW" | python3 -c "import json,sys;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else 'NONE')" 2>/dev/null)
}

f10_leg() { # $1=mode $2=leg  (submitted doc ends in 0; fault rotates trailing digit -> 1)
  [ "$1" = corrupt ] && toggle "/api/v1/contactservice/contacts/test/faultmode/corrupt"
  mkuser "10$2"; local DOC="F10DOC$(date +%s | cut -c6-10)0"; mkcontact "$DOC"; lefttrip
  preserve_and_read
  local STORED; STORED=$(curl -s -H "Authorization: Bearer $U_TOK" "$GW/api/v1/contactservice/contacts/account/$U_ID" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data'][0]['documentNumber'])" 2>/dev/null)
  toggle "/api/v1/contactservice/contacts/test/faultmode/none"
  echo "F10 LEG=$2 mode=$1 trip=$TRIP_ID submitted_doc=$DOC stored_contact_doc=$STORED order=$ORD_ID order_doc=$ORD_DOC ack=$ACK" | tee -a "$EVROOT/fcorpus-f10/legs.log"
}

f14_leg() { # $1=mode $2=leg
  [ "$1" = corrupt ] && toggle "/api/v1/basicservice/basic/test/faultmode/corrupt"
  mkuser "14$2"; mkcontact "F14DOC$(date +%s | cut -c6-10)5"; lefttrip
  preserve_and_read
  toggle "/api/v1/basicservice/basic/test/faultmode/none"
  echo "F14 LEG=$2 mode=$1 trip=$TRIP_ID search_price=$SEARCH_PRICE order=$ORD_ID order_price=$ORD_PRICE ack=$ACK" | tee -a "$EVROOT/fcorpus-f14/legs.log"
}

ONLY="${1:-all}"
mkdir -p "$EVROOT/fcorpus-f10" "$EVROOT/fcorpus-f14"
if [ "$ONLY" = all ] || [ "$ONLY" = f10 ]; then : > "$EVROOT/fcorpus-f10/legs.log"; echo "=== F10 ==="; f10_leg none CTRL; f10_leg corrupt FLT; fi
if [ "$ONLY" = all ] || [ "$ONLY" = f14 ]; then : > "$EVROOT/fcorpus-f14/legs.log"; echo "=== F14 ==="; f14_leg none CTRL; f14_leg corrupt FLT; fi
echo "=== legs done ($ONLY) ==="
