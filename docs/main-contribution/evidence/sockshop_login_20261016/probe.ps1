# Sock Shop pre-run probe: port-forwards (kept running; PIDs saved), a catalogue read, a register-as-login,
# and POST /addresses with and without the session cookie.
$ErrorActionPreference = "Continue"
$dir = "<run dir>"
function Listening($port) { return [bool](Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) }
$pids = @()
if (-not (Listening 16686)) {
  $p = Start-Process -FilePath "wsl.exe" -ArgumentList "-d","Ubuntu","--","kubectl","port-forward","-n","istio-system","svc/tracing","16686:80" -WindowStyle Hidden -PassThru
  $pids += $p.Id; "started jaeger pf $($p.Id)"
} else { "16686 already listening" }
if (-not (Listening 8080)) {
  $p = Start-Process -FilePath "wsl.exe" -ArgumentList "-d","Ubuntu","--","kubectl","port-forward","-n","sock-shop","svc/front-end","8080:80" -WindowStyle Hidden -PassThru
  $pids += $p.Id; "started front-end pf $($p.Id)"
} else { "8080 already listening" }
Set-Content -Path (Join-Path $dir "pf_pids.txt") -Value ($pids -join ",")
Start-Sleep -Seconds 8

function Show($label, $script) {
  try {
    $r = & $script
    $txt = if ($r.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($r.Content) } else { [string]$r.Content }
    "$label -> $($r.StatusCode) $($txt.Substring(0, [math]::Min(160, $txt.Length)))"
  }
  catch { $c = $null; if ($_.Exception.Response) { $c = [int]$_.Exception.Response.StatusCode }; "$label -> $c $($_.Exception.Message)" }
}
Show "GET /catalogue" { Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/catalogue?size=2" -TimeoutSec 20 }
$sess = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$u = "probe" + (Get-Random -Maximum 999999)
$reg = '{"username":"' + $u + '","password":"probepw","email":"' + $u + '@mist.test","firstName":"m","lastName":"t"}'
Show "POST /register" { Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/register" -Method Post -Body $reg -ContentType "application/json" -WebSession $sess -TimeoutSec 20 }
"cookies: " + (($sess.Cookies.GetCookies("http://localhost:8080") | ForEach-Object { $_.Name }) -join ",")
$addr = '{"street":"Main","number":"1","country":"US","city":"Waco","postcode":"76706"}'
Show "POST /addresses (session)" { Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/addresses" -Method Post -Body $addr -ContentType "application/json" -WebSession $sess -TimeoutSec 20 }
Show "POST /addresses (no session)" { Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/addresses" -Method Post -Body $addr -ContentType "application/json" -TimeoutSec 20 }
Show "GET jaeger services" { Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:16686/jaeger/api/services" -TimeoutSec 20 }
