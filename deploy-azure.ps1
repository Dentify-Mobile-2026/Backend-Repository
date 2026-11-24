<#
.SYNOPSIS
  Despliega (de forma idempotente) MySQL Flexible Server + Spring Boot Backend de Dentify en Azure for Students.
.EXAMPLE
  .\deploy-azure.ps1 -AppName "dentify-api-upc" -DbPassword "TuPasswordRealSegura123!"
#>
param(
    [string]$ResourceGroup = "rg-dentify",
    [string]$Location = "eastus",
    [string]$MysqlLocation = "",
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
            throw "Fallo en '$Label' (exit code $LASTEXITCODE)."
        }
    } finally {
        $ErrorActionPreference = $prev
    }
}

Write-Host "==> 1/6 Verificando sesion de Azure..." -ForegroundColor Cyan
Invoke-Step "az account show" { az account show --output table }

Write-Host "==> 2/6 Asegurando Resource Group '$ResourceGroup' en '$Location'..." -ForegroundColor Cyan
$rgExists = az group exists --name $ResourceGroup
if ($rgExists -ne "true") {
    Invoke-Step "az group create" { az group create --name $ResourceGroup --location $Location --output none }
} else {
    Write-Host "    Resource Group '$ResourceGroup' ya existe (reutilizando)." -ForegroundColor DarkGray
}

# Reutilizar MySQL existente en el Resource Group si ya fue creado antes
$existingMysql = (az mysql flexible-server list --resource-group $ResourceGroup --query "[0].name" -o tsv 2>$null)
if ($existingMysql) {
    $MysqlServer = $existingMysql.Trim()
    Write-Host "==> 3/6 Reutilizando MySQL Flexible Server existente '$MysqlServer'..." -ForegroundColor Cyan
} else {
    if (-not $MysqlLocation) {
        Write-Host "==> 3/6 Buscando region habilitada para MySQL Flexible Server (Burstable B1ms)..." -ForegroundColor Cyan
        foreach ($candidate in @("chilecentral", "mexicocentral", "westus2", $Location, "brazilsouth", "eastus2")) {
            $sku = (az mysql flexible-server list-skus --location $candidate --query "[0].supportedFlexibleServerEditions[?name=='Burstable'].supportedServerVersions[0].supportedSkus[0].name" -o tsv 2>$null)
            if ($sku -and $sku.Trim().Length -gt 0) {
                $MysqlLocation = $candidate
                break
            }
        }
        if (-not $MysqlLocation) {
            throw "No se encontro ninguna region con SKU Burstable disponible para MySQL en esta suscripcion."
        }
    }

    Write-Host "    Creando MySQL 8.0 Flexible Server '$MysqlServer' en '$MysqlLocation' (Standard_B1ms)..." -ForegroundColor Cyan
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
}

$dbExists = (az mysql flexible-server db show --resource-group $ResourceGroup --server-name $MysqlServer --database-name dentify --query "name" -o tsv 2>$null)
if (-not $dbExists) {
    Invoke-Step "az mysql flexible-server db create" {
        az mysql flexible-server db create `
            --resource-group $ResourceGroup `
            --server-name $MysqlServer `
            --database-name dentify `
            --charset utf8mb4 `
            --collation utf8mb4_unicode_ci `
            --output none
    }
}

Write-Host "==> 4/6 Configurando App Service Plan B1 y Web App Java 17 '$AppName'..." -ForegroundColor Cyan
$planName = "plan-dentify"
$planExists = (az appservice plan show --name $planName --resource-group $ResourceGroup --query "name" -o tsv 2>$null)
if (-not $planExists) {
    Invoke-Step "az appservice plan create" {
        az appservice plan create --name $planName --resource-group $ResourceGroup --location $Location --sku B1 --is-linux --output none
    }
}

$appExists = (az webapp show --name $AppName --resource-group $ResourceGroup --query "name" -o tsv 2>$null)
if (-not $appExists) {
    Invoke-Step "az webapp create" {
        az webapp create --resource-group $ResourceGroup --plan $planName --name $AppName --runtime "JAVA:17-java17" --output none
    }
} else {
    Write-Host "    Web App '$AppName' ya existe (reutilizando)." -ForegroundColor DarkGray
}

$jdbcUrl = "jdbc:mysql://${MysqlServer}.mysql.database.azure.com:3306/dentify?useSSL=true&requireSSL=true&serverTimezone=America/Lima"

Invoke-Step "az webapp config appsettings set" {
    az webapp config appsettings set --resource-group $ResourceGroup --name $AppName --settings `
        DB_URL="$jdbcUrl" `
        DB_USERNAME="$DbAdmin" `
        DB_PASSWORD="$DbPassword" `
        JWT_SECRET="$JwtSecret" `
        PORT="8080" `
        WEBSITES_PORT="8080" `
        CORS_ALLOWED_ORIGINS="*" `
        SWAGGER_ENABLED="false" `
        --output none
}

Write-Host "==> 5/6 Compilando JAR de Spring Boot..." -ForegroundColor Cyan
if (-not $env:JAVA_HOME -and (Test-Path "C:\Program Files\Java\jdk-17.0.12")) {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.12"
}
Invoke-Step "mvnw package" { .\mvnw.cmd clean package -DskipTests }
$jarPath = (Get-ChildItem -Path "target" -Filter "*.jar" | Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1).FullName

Write-Host "==> 6/6 Subiendo JAR a https://$AppName.azurewebsites.net ..." -ForegroundColor Cyan
Invoke-Step "az webapp deploy" {
    az webapp deploy --resource-group $ResourceGroup --name $AppName --src-path $jarPath --type jar
}

Write-Host ""
Write-Host "LISTO. URL gratuita con HTTPS:" -ForegroundColor Green
Write-Host "  Backend API : https://$AppName.azurewebsites.net/api/"
Write-Host "  Health Check: https://$AppName.azurewebsites.net/health"
Write-Host "  MySQL Host  : ${MysqlServer}.mysql.database.azure.com"
