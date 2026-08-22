# Build and run the app.
#   .\run.ps1            -> port 8080
#   .\run.ps1 -Port 8081 -> another port
#
# Why not `mvnw spring-boot:run`? Its forked compile round has produced corrupt
# MapStruct output on this project (BookingMapperImpl written with MissingTypes),
# which shows up as "BookingMapper bean could not be found". Building the jar
# first compiles once, cleanly.
param([int]$Port = 8080)

Write-Host "Building..." -ForegroundColor Cyan
& ".\mvnw.cmd" -B clean package -DskipTests
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed." -ForegroundColor Red; exit 1 }

Write-Host "Starting on port $Port (Ctrl+C to stop)" -ForegroundColor Green
java "-Duser.timezone=UTC" -jar target\hotel-pms-0.0.1-SNAPSHOT.jar --server.port=$Port
