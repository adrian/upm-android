Invoke-WebRequest http://www.bouncycastle.org/csharp/download/bccrypto-net-1.7-bin.zip -OutFile bccrypto-net-1.7-bin.zip
(New-Object -ComObject Shell.Application).NameSpace([System.Environment]::CurrentDirectory).CopyHere((New-Object -ComObject Shell.Application).NameSpace("bccrypto-net-1.7-bin.zip").Items(),16)
Add-Type -LiteralPath BouncyCastle.Crypto.dll
$content=Get-Content -LiteralPath upm.db -Encoding Byte
"The file header is `"$([System.Text.Encoding]::ASCII.GetString($content[0..2]))`""
"The version is $($content[3])"
$salt=$content[4..11]
"The salt is $salt"
$content=$content[12..($content.Length-1)]
$cipher=(New-Object Org.BouncyCastle.Crypto.Paddings.PaddedBufferedBlockCipher (New-Object Org.BouncyCastle.Crypto.Modes.CbcBlockCipher (New-Object Org.BouncyCastle.Crypto.Engines.AesEngine)),(New-Object Org.BouncyCastle.Crypto.Paddings.Pkcs7Padding))
$generator=New-Object Org.BouncyCastle.Crypto.Generators.Pkcs12ParametersGenerator (New-Object Org.BouncyCastle.Crypto.Digests.Sha256Digest)
$generator.Init([Org.BouncyCastle.Crypto.Generators.Pkcs12ParametersGenerator]::Pkcs12PasswordToBytes($password.ToCharArray()),$salt,20)
$cipher.Init($false,$generator.GenerateDerivedParameters(256,128))
$content=$cipher.DoFinal($content)
$stream=New-Object System.IO.MemoryStream -ArgumentList (,$content)
function get {
    $buffer=[byte[]](,0*4)
    $stream.Read($buffer,0,4)|Out-Null
    $number=[int][System.Text.Encoding]::ASCII.GetString($buffer)
    $buffer=[byte[]](,0*$number)
    $stream.Read($buffer,0,$number)|Out-Null
    return [System.Text.Encoding]::ASCII.GetString($buffer)
}
"The revision is $(get)"
"The remote location is `"$(get)`""
"The authentication database entry is `"$(get)`""
$accounts=@()
while($stream.Position-lt$stream.Length){
    $accounts+=@{
        "Account Name"=get
        "User ID"=get
        "Password"=get
        "URL"=get
        "Notes"=get
    }
}
$accounts|ConvertTo-Json|Set-Content -Encoding UTF8 upm.json