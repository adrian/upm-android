Invoke-WebRequest http://www.bouncycastle.org/csharp/download/bccrypto-net-1.7-bin.zip -OutFile bccrypto-net-1.7-bin.zip
(New-Object -ComObject Shell.Application).NameSpace([System.Environment]::CurrentDirectory).CopyHere((New-Object -ComObject Shell.Application).NameSpace("bccrypto-net-1.7-bin.zip").Items(),16)
Add-Type -LiteralPath BouncyCastle.Crypto.dll
$salt=,0*8
$stream=New-Object System.IO.MemoryStream
function put([string]$content){
    $bytes=[System.Text.Encoding]::UTF8.GetBytes($content)
    $stream.Write([System.Text.Encoding]::ASCII.GetBytes([string]::Format("{0:D4}",$bytes.Length)),0,4)
    $stream.Write($bytes,0,$bytes.Length)
}
put "$revision"
put "${Remote Location}"
put "${Authentication Database Entry}"
(Get-Content -Raw -Encoding UTF8 upm.json|ConvertFrom-Json)|ForEach-Object{
    put $_."Account Name"
    put $_."User ID"
    put $_."Password"
    put $_."URL"
    put $_."Notes"
}
[string]::Join(" ",$stream.ToArray())
[System.Text.Encoding]::ASCII.GetString($stream.ToArray())
$cipher=(New-Object Org.BouncyCastle.Crypto.Paddings.PaddedBufferedBlockCipher (New-Object Org.BouncyCastle.Crypto.Modes.CbcBlockCipher (New-Object Org.BouncyCastle.Crypto.Engines.AesEngine)),(New-Object Org.BouncyCastle.Crypto.Paddings.Pkcs7Padding))
$generator=New-Object Org.BouncyCastle.Crypto.Generators.Pkcs12ParametersGenerator (New-Object Org.BouncyCastle.Crypto.Digests.Sha256Digest)
$generator.Init([Org.BouncyCastle.Crypto.Generators.Pkcs12ParametersGenerator]::Pkcs12PasswordToBytes($password.ToCharArray()),$salt,20)
$cipher.Init($true,$generator.GenerateDerivedParameters(256,128))
Set-Content -Value ([byte[]]([System.Text.Encoding]::ASCII.GetBytes("UPM")+3+$salt+$cipher.DoFinal($stream.ToArray()))) -Encoding Byte upm.db