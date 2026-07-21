#!/bin/sh
# TeaStore order journey (POSIX sh, runs inside the curl pod). $1 = marker in address1.
MARKER="$1"
W=http://teastore-webui:8080/tools.descartes.teastore.webui
CJ=/tmp/cj-$MARKER
curl -s -c $CJ -b $CJ -o /dev/null -w 'login %{http_code}\n' -X POST $W/loginAction -d 'username=user21&password=password'
curl -s -c $CJ -b $CJ -o /dev/null -w 'category %{http_code}\n' "$W/category?category=2&page=1"
curl -s -c $CJ -b $CJ -o /dev/null -w 'addToCart %{http_code}\n' -X POST $W/cartAction -d 'addToCart=&productid=42'
curl -s -c $CJ -b $CJ -o /tmp/confirm-$MARKER.html -w 'confirm %{http_code}\n' -X POST $W/cartAction -d "firstname=Order&lastname=Journey&address1=$MARKER&address2=City1&cardtype=volvo&cardnumber=314159265359&expirydate=12/2030&confirm=Confirm"
# masking indicator: does the returned page carry order-confirmed content (not an error page)?
echo -n 'confirm-page order-mentions: '; grep -ci 'order' /tmp/confirm-$MARKER.html 2>/dev/null || echo 0
echo -n 'confirm-page error-mentions: '; grep -ci 'error\|exception\|503\|500' /tmp/confirm-$MARKER.html 2>/dev/null || echo 0
