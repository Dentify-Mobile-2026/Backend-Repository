<#
.SYNOPSIS
  Despliegue ordenado y limpio desde cero en Azure for Students:
    Paso 1: Base de datos MySQL 8.0 Flexible Server + BD dentify
    Paso 2: App Service (Java 17) conectado a esa BD mediante variables de entorno JSON
    Paso 3: Compilar JAR (Spring Boot) + subir a Azure + verificar /health
.EXAMPLE
  .\deploy-azure.ps1 -AppName "dentify-api-upc" -DbPassword "TuPasswordRealSegura123!"
#>
param(
    [string]$ResourceGroup = "rg-dentify",
    [string]$AppLocation = "eastus",
    [string]$MysqlLocation = "chilecentral",
    [string]$AppName = "dentify-api-$((Get-Random -Minimum 1000 -Maximum 9999))",
    [string]$MysqlServer = "dentify-mysql-$((Get-Random -Minimum 1000 -Maximum 9999))",
    [string]$DbAdmin = "dentifyadmin",
    [Parameter(Mandatory = $true)]
    [string]$DbPassword,
    [string]$JwtSecret = (-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 48 | ForEach-Object { [char]$_ }))
)

$ErrorActionPreference = "Stop"

function Invoke-Step {
    param(
        [string]$Label,
        [scriptblock]$Command
    )
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $Command
        if ($LASTEXITCODE -ne 0) {
            throw "DETENIDO en '$Label' (exit code $LASTEXITCODE). No se continuo con los siguientes pasos."
        }
    } finally {
        $ErrorActionPreference = $prev
    }
}

Write-Host "==> Verificando sesion de Azure..." -ForegroundColor Cyan
Invoke-Step "az account show" { az account show --output table }

Write-Host "==> Creando Resource Group '$ResourceGroup'..." -ForegroundColor Cyan
Invoke-Step "az group create" { az group create --name $ResourceGroup --location $AppLocation --output none }

# ------------------------------------------------------------------
# PASO 1: BASE DE DATOS MYSQL PRIMERO (obligatorio antes del backend)
# ------------------------------------------------------------------
Write-Host "==> [PASO 1/3] Creando MySQL 8.0 '$MysqlServer' en '$MysqlLocation' (Burstable B1ms)..." -ForegroundColor Cyan
Invoke-Step "az mysql flexible-server create" {
    az mysql flexible-server create `
        --resource-group $ResourceGroup `
        --name $MysqlServer `
        --location $MysqlLocation `
        --admin-user $DbAdmin `
        --admin-password $DbPassword `
        --sku-name Standard_B1ms `
        --tier Burstable `
        --version 8.0.21 `
        --storage-size 20 `
        --public-access 0.0.0.0 `
        --yes `
        --output none
}

Invoke-Step "az mysql flexible-server db create" {
    az mysql flexible-server db create `
        --resource-group $ResourceGroup `
        --server-name $MysqlServer `
        --database-name dentify `
        --charset utf8mb4 `
        --collation utf8mb4_unicode_ci `
        --output none
}

$mysqlHost = "${MysqlServer}.mysql.database.azure.com"
$jdbcUrl = "jdbc:mysql://${mysqlHost}:3306/dentify?useSSL=true&requireSSL=true&serverTimezone=America/Lima"
Write-Host "    BD lista: $mysqlHost / database: dentify" -ForegroundColor Green

# ------------------------------------------------------------------
# PASO 2: APP SERVICE CONECTADO A LA BD VIA ARCHIVO JSON (evita bug '&' de cmd.exe)
# ------------------------------------------------------------------
Write-Host "==> [PASO 2/3] Creando App Service Plan B1 y Web App '$AppName' en '$AppLocation'..." -ForegroundColor Cyan
$planName = "plan-dentify"
Invoke-Step "az appservice plan create" {
    az appservice plan create --name $planName --resource-group $ResourceGroup --location $AppLocation --sku B1 --is-linux --output none
}
Invoke-Step "az webapp create" {
    az webapp create --resource-group $ResourceGroup --plan $planName --name $AppName --runtime "JAVA:17-java17" --output none
}

$settingsPath = [System.IO.Path]::GetTempFileName()
try {
    $settingsObj = @{
        DB_URL               = $jdbcUrl
        DB_USERNAME          = $DbAdmin
        DB_PASSWORD          = $DbPassword
        JWT_SECRET           = $JwtSecret
        PORT                 = "8080"
        WEBSITES_PORT        = "8080"
        CORS_ALLOWED_ORIGINS = "*"
        SWAGGER_ENABLED      = "false"
    }
    $settingsObj | ConvertTo-Json | Set-Content -Path $settingsPath -Encoding UTF8

    Write-Host "    Adjuntando variables de entorno (DB_URL, JWT_SECRET, etc.) a '$AppName'..." -ForegroundColor Cyan
    Invoke-Step "az webapp config appsettings set" {
        az webapp config appsettings set --resource-group $ResourceGroup --name $AppName --settings "@$settingsPath" --output none
    }
} finally {
    Remove-Item -Path $settingsPath -ErrorAction SilentlyContinue
}

# ------------------------------------------------------------------
# PASO 3: COMPILAR JAR Y DESPLEGAR BACKEND + VERIFICAR /health
# ------------------------------------------------------------------
Write-Host "==> [PASO 3/3] Compilando JAR y desplegando en https://$AppName.azurewebsites.net ..." -ForegroundColor Cyan
if (-not $env:JAVA_HOME -and (Test-Path "C:\Program Files\Java\jdk-17.0.12")) {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.12"
}
Invoke-Step "mvnw clean package" { .\mvnw.cmd clean package -DskipTests }
$jarPath = (Get-ChildItem -Path "target" -Filter "*.jar" | Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1).FullName

Invoke-Step "az webapp deploy" {
    az webapp deploy --resource-group $ResourceGroup --name $AppName --src-path $jarPath --type jar
}

Write-Host ""
Write-Host "DESPLIEGUE BACKEND + BD COMPLETADO:" -ForegroundColor Green
Write-Host "  1) Health Check : https://$AppName.azurewebsites.net/health"
Write-Host "  2) URL para APK : https://$AppName.azurewebsites.net/api/"
Write-Host "  3) MySQL Host   : $mysqlHost"
Write-Host ""
Write-Host "Siguiente paso (Flutter APK):" -ForegroundColor Yellow
Write-Host "  cd ..\Front-Flutter"
Write-Host "  .\build-apk.ps1 -ApiBaseUrl `"https://$AppName.azurewebsites.net/api/`""
