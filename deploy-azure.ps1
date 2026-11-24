<#
.SYNOPSIS
  Despliega el Backend Spring Boot (Java 17) en Azure App Service conectandolo a una base MySQL externa (ej. Aiven).
.EXAMPLE
  .\deploy-azure.ps1 -AppName "dentify-api-upc" -DbHost "mysql-dentify-sebastian-4632.a.aivencloud.com" -DbPort 19848 -DbName "defaultdb" -DbUsername "avnadmin" -DbPassword "TU_PASSWORD"
#>
param(
    [string]$ResourceGroup = "rg-dentify",
    [string]$Location = "eastus",
    [string]$AppName = "dentify-api-upc",
    [Parameter(Mandatory = $true)]
    [string]$DbHost,
    [int]$DbPort = 19848,
    [string]$DbName = "defaultdb",
    [string]$DbUsername = "avnadmin",
    [Parameter(Mandatory = $true)]
    [string]$DbPassword,
    [string]$SwaggerEnabled = "true",
    [string]$JwtSecret = (-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 48 | ForEach-Object { [char]$_ }))
)

$ErrorActionPreference = "Continue"

function Invoke-Step {
    param(
        [string]$Label,
        [scriptblock]$Command
    )
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "DETENIDO en '$Label' (exit code $LASTEXITCODE)."
    }
}

Write-Host "==> [PASO 1/4] Verificando que la BD externa '${DbHost}:${DbPort}' este activa..." -ForegroundColor Cyan
$tcpOk = Test-NetConnection -ComputerName $DbHost -Port $DbPort -WarningAction SilentlyContinue
if (-not $tcpOk.TcpTestSucceeded) {
    throw "No se pudo conectar a ${DbHost}:${DbPort}. Verifica en Aiven que el servicio ya aparezca en estado 'Running'."
}
Write-Host "    Conexion TCP a MySQL confirmada." -ForegroundColor Green

$jdbcUrl = "jdbc:mysql://${DbHost}:${DbPort}/${DbName}?sslMode=REQUIRED&serverTimezone=America/Lima"

Write-Host "==> [PASO 2/4] Verificando sesion de Azure y creando App Service '$AppName'..." -ForegroundColor Cyan
Invoke-Step "az account show" { az account show --output table }

$rgExists = (az group exists --name $ResourceGroup 2>$null | Out-String).Trim()
if ($rgExists -ne "true") {
    Invoke-Step "az group create" { az group create --name $ResourceGroup --location $Location --output none }
}

$planName = "plan-dentify"
$planExists = (az appservice plan show --name $planName --resource-group $ResourceGroup --query "name" -o tsv 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or -not $planExists) {
    Invoke-Step "az appservice plan create" {
        az appservice plan create --name $planName --resource-group $ResourceGroup --location $Location --sku B1 --is-linux --output none
    }
}

$appExists = (az webapp show --name $AppName --resource-group $ResourceGroup --query "name" -o tsv 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or -not $appExists) {
    Invoke-Step "az webapp create" {
        az webapp create --resource-group $ResourceGroup --plan $planName --name $AppName --runtime "JAVA:17-java17" --output none
    }
}

Write-Host "==> [PASO 3/4] Adjuntando variables de entorno (DB_URL, JWT_SECRET, etc.)..." -ForegroundColor Cyan
$settingsPath = [System.IO.Path]::GetTempFileName()
try {
    $settingsObj = @{
        DB_URL               = $jdbcUrl
        DB_USERNAME          = $DbUsername
        DB_PASSWORD          = $DbPassword
        JWT_SECRET           = $JwtSecret
        PORT                 = "8080"
        WEBSITES_PORT        = "8080"
        CORS_ALLOWED_ORIGINS = "*"
        SWAGGER_ENABLED      = $SwaggerEnabled
    }
    $settingsObj | ConvertTo-Json | Set-Content -Path $settingsPath -Encoding UTF8

    Invoke-Step "az webapp config appsettings set" {
        az webapp config appsettings set --resource-group $ResourceGroup --name $AppName --settings "@$settingsPath" --output none
    }
} finally {
    Remove-Item -Path $settingsPath -ErrorAction SilentlyContinue
}

Write-Host "==> [PASO 4/4] Compilando JAR y desplegando en https://$AppName.azurewebsites.net ..." -ForegroundColor Cyan
if (-not $env:JAVA_HOME -and (Test-Path "C:\Program Files\Java\jdk-17.0.12")) {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.12"
}
Invoke-Step "mvnw clean package" { .\mvnw.cmd clean package -DskipTests }
$jarPath = (Get-ChildItem -Path "target" -Filter "*.jar" | Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1).FullName

Invoke-Step "az webapp deploy" {
    az webapp deploy --resource-group $ResourceGroup --name $AppName --src-path $jarPath --type jar
}

Write-Host ""
Write-Host "DESPLIEGUE COMPLETADO:" -ForegroundColor Green
Write-Host "  Health Check : https://$AppName.azurewebsites.net/health"
Write-Host "  URL para APK : https://$AppName.azurewebsites.net/api/"
