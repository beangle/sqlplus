[CmdletBinding()]
param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$SqlplusArgs
)

$ErrorActionPreference = "Stop"

if (!$SqlplusArgs -or $SqlplusArgs.Count -eq 0) {
  Write-Host @"
Usage:
  sqlplus.ps1 C:\path\to\db.xml
  sqlplus.ps1 transport C:\path\to\conversion.xml
  sqlplus.ps1 validate C:\path\to\basis.xml
"@
  exit 1
}

$remoteRepo = if ($env:M2_REMOTE_REPO) {
  $env:M2_REMOTE_REPO.TrimEnd("/")
} else {
  "https://maven.aliyun.com/repository/public"
}

$localRepo = if ($env:M2_REPO) {
  $env:M2_REPO
} else {
  Join-Path $HOME ".m2\repository"
}

$classpathEntries = [System.Collections.Generic.List[string]]::new()

function Add-MavenArtifact {
  param(
    [Parameter(Mandatory = $true)][string]$GroupId,
    [Parameter(Mandatory = $true)][string]$ArtifactId,
    [Parameter(Mandatory = $true)][string]$Version
  )

  $groupPath = $GroupId.Replace(".", "/")
  $relativePath = "$groupPath/$ArtifactId/$Version/$ArtifactId-$Version.jar"
  $localFile = Join-Path $localRepo ($relativePath.Replace("/", "\"))
  $classpathEntries.Add($localFile)

  if (!(Test-Path -LiteralPath $localFile -PathType Leaf)) {
    $targetDir = Split-Path -Parent $localFile
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

    $url = "$remoteRepo/$relativePath"
    $partialFile = "$localFile.part"
    Write-Host "fetching $url"
    try {
      Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $partialFile
      Move-Item -LiteralPath $partialFile -Destination $localFile -Force
    } catch {
      if (Test-Path -LiteralPath $partialFile) {
        Remove-Item -LiteralPath $partialFile -Force
      }
      throw "Cannot download $url`: $($_.Exception.Message)"
    }
  }
}

$scalaVer = "2.13.16"
$scala3Ver = "3.3.7"
$beangleCommonsVer = "6.2.1"
$beangleTemplateVer = "0.2.8"
$slf4jVer = "2.0.18"
$logbackVer = "1.5.34"
$commonsCompressVer = "1.28.0"
$bootVer = "0.1.28"
$beangleSqlplusVer = "0.2.4"

Add-MavenArtifact "org.scala-lang" "scala-library" $scalaVer
Add-MavenArtifact "org.scala-lang" "scala-reflect" $scalaVer
Add-MavenArtifact "org.scala-lang" "scala3-library_3" $scala3Ver
Add-MavenArtifact "org.beangle.commons" "beangle-commons" $beangleCommonsVer
Add-MavenArtifact "org.apache.commons" "commons-compress" $commonsCompressVer
Add-MavenArtifact "org.beangle.boot" "beangle-boot" $bootVer
Add-MavenArtifact "org.slf4j" "slf4j-api" $slf4jVer
Add-MavenArtifact "ch.qos.logback" "logback-core" $logbackVer
Add-MavenArtifact "ch.qos.logback" "logback-classic" $logbackVer
Add-MavenArtifact "org.beangle.sqlplus" "beangle-sqlplus" $beangleSqlplusVer

$sqlplusJar = Join-Path $localRepo "org\beangle\sqlplus\beangle-sqlplus\$beangleSqlplusVer\beangle-sqlplus-$beangleSqlplusVer.jar"
$bootClasspath = $classpathEntries -join [IO.Path]::PathSeparator

& java -cp $bootClasspath org.beangle.boot.dependency.AppResolver $sqlplusJar "--remote=$remoteRepo" "--local=$localRepo" *> $null
if ($LASTEXITCODE -ne 0) {
  throw "Dependency resolution failed with exit code $LASTEXITCODE."
}

$launcherInfo = (& java -cp $bootClasspath org.beangle.boot.launcher.Classpath $sqlplusJar $localRepo | Out-String).Trim()
if ($LASTEXITCODE -ne 0) {
  throw "Classpath generation failed with exit code $LASTEXITCODE`: $launcherInfo"
}

$separator = $launcherInfo.IndexOf("@")
if ($separator -lt 1) {
  throw "Unexpected launcher output: $launcherInfo"
}

$mainClass = $launcherInfo.Substring(0, $separator)
$appClasspath = $launcherInfo.Substring($separator + 1)

& java -cp $appClasspath $mainClass @SqlplusArgs
exit $LASTEXITCODE
