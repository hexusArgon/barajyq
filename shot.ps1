param($n)
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s emulator-5554 shell screencap -p /sdcard/s.png
& $adb -s emulator-5554 pull /sdcard/s.png "C:\Users\eseid\barajyq\$n.png" | Out-Null
[IO.File]::WriteAllText("C:\Users\eseid\barajyq\$n.b64", [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\eseid\barajyq\$n.png")))
