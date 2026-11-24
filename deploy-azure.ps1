<#
.SYNOPSIS
  Despliega MySQL Flexible Server + Spring Boot Backend de Dentify en Azure for Students en un solo flujo.
.EXAMPLE
  .\deploy-azure.ps1 -ResourceGroup "rg-dentify" -Location "eastus" -DbPassword "TuPasswordSeguro123!"
#>
param(
    [string]$ResourceGroup = "rg-dentify",
    [string]$Location = "eastus",
    [string]$AppName = "dentify-api-$((Get-Random -Minimum 1000 -Maximum 9999))",
    [string]$MysqlServer = "dentify-mysql-$((Get-Random -Minimum 1000 -Maximum 9999))",
    [string]$DbAdmin = "dentifyadmin",
    [Parameter(Mandatory = $true)]
    [string]$DbPassword,
    [string]$JwtSecret = (-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 48 | ForEach-Object { [char]$_ }))
)

$ErrorActionPreference = "Stop"

Write-Host "==> 1/6 Verificando sesion de Azure..." -ForegroundColor Cyan
az account show --output table

Write-Host "==> 2/6 Creando Resource Group '$ResourceGroup' en '$Location'..." -ForegroundColor Cyan
az group create --name $ResourceGroup --location $Location --output none

Write-Host "==> 3/6 Creando MySQL 8.0 Flexible Server '$MysqlServer' (Burstable B1ms)..." -ForegroundColor Cyan
az mysql flexible-server create `
    --resource-group $ResourceGroup `
    --name $MysqlServer `
    --location $Location `
    --admin-user $DbAdmin `
    --admin-password $DbPassword `
    --sku-name Standard_B1ms `
    --tier Burstable `
    --version 8.0.21 `
    --storage-size 20 `
    --public-access 0.0.0.0 `
    --yes `
    --output none

az mysql flexible-server db create `
    --resource-group $ResourceGroup `
    --server-name $MysqlServer `
    --database-name dentify `
    --charset utf8mb4 `
    --collation utf8mb4_unicode_ci `
    --output none

Write-Host "==> 4/6 Creando App Service Plan B1 y Web App Java 17 '$AppName'..." -ForegroundColor Cyan
$planName = "plan-dentify"
az appservice plan create --name $planName --resource-group $ResourceGroup --sku B1 --is-linux --output none
az webapp create --resource-group $ResourceGroup --plan $planName --name $AppName --runtime "JAVA:17-java17" --output none

$jdbcUrl = "jdbc:mysql://${MysqlServer}.mysql.database.azure.com:3306/dentify?useSSL=true&requireSSL=true&serverTimezone=America/Lima"

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

Write-Host "==> 5/6 Compilando JAR de Spring Boot..." -ForegroundColor Cyan
if (-not $env:JAVA_HOME -and (Test-Path "C:\Program Files\Java\jdk-17.0.12")) {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.12"
}
.\mvnw.cmd clean package -DskipTests
$jarPath = (Get-ChildItem -Path "target" -Filter "*.jar" | Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1).FullName

Write-Host "==> 6/6 Subiendo JAR a https://$AppName.azurewebsites.net ..." -ForegroundColor Cyan
az webapp deploy --resource-group $ResourceGroup --name $AppName --src-path $jarPath --type jar

Write-Host ""
Write-Host "LISTO. URL gratuita con HTTPS:" -ForegroundColor Green
Write-Host "  Backend API : https://$AppName.azurewebsites.net/api/"
Write-Host "  Health Check: https://$AppName.azurewebsites.net/health"
Write-Host "  MySQL Host  : ${MysqlServer}.mysql.database.azure.com"
