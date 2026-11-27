param(
    [Parameter(Mandatory = $true)] [string] $Normal,
    [Parameter(Mandatory = $true)] [string] $Burst
)

$ErrorActionPreference = 'Stop'
$resumen = foreach ($ensayo in @(
    @{ Nombre = 'normal'; Archivo = $Normal },
    @{ Nombre = 'burst'; Archivo = $Burst }
)) {
    $filas = @(Import-Csv -LiteralPath $ensayo.Archivo)
    if ($filas.Count -eq 0) { throw "El ensayo $($ensayo.Nombre) está vacío." }
    $operacionesEsperadas = @('GET csrf - ráfaga', 'POST login - cuota agotada')
    if ($ensayo.Nombre -eq 'normal') {
        $operacionesEsperadas = @('GET catálogo', 'GET consumo', 'GET csrf - inicial',
            'GET csrf - post login', 'POST disponibilidad', 'POST login - Argon2id', 'POST preferencias')
    }
    $operacionesObservadas = @($filas.label | Sort-Object -Unique)
    if (Compare-Object -ReferenceObject $operacionesEsperadas -DifferenceObject $operacionesObservadas) {
        throw "El ensayo $($ensayo.Nombre) tiene operaciones ausentes o inesperadas."
    }
    if ($ensayo.Nombre -eq 'burst' -and $filas.Count -ne 40) {
        throw 'La ráfaga debe contener 20 solicitudes CSRF y 20 intentos de login.'
    }
    foreach ($grupo in ($filas | Group-Object label)) {
        $codigoEsperado = '200'
        if ($ensayo.Nombre -eq 'burst' -and $grupo.Name -eq 'POST login - cuota agotada') {
            $codigoEsperado = '429'
        }
        $errores = @($grupo.Group | Where-Object {
            $_.success -ne 'true' -or $_.responseCode -ne $codigoEsperado
        }).Count
        $latencias = @($grupo.Group | ForEach-Object { [double]$_.elapsed } | Sort-Object)
        $p95 = $latencias[[int][Math]::Ceiling(0.95 * $latencias.Count) - 1]
        [pscustomobject]@{
            Ensayo = $ensayo.Nombre
            Operacion = $grupo.Name
            Muestras = $grupo.Count
            MediaMs = [Math]::Round(($latencias | Measure-Object -Average).Average, 2)
            P95Ms = $p95
            MaxMs = $latencias[-1]
            Errores = $errores
            CodigoEsperado = $codigoEsperado
        }
    }
}

$resumen | Export-Csv -LiteralPath (Join-Path (Split-Path -Parent (Resolve-Path $Normal)) 'resumen.csv') -NoTypeInformation -Encoding utf8
$resumen | Format-Table -AutoSize
$incumplimientos = @($resumen | Where-Object { $_.Errores -gt 0 -or $_.P95Ms -ge 500 })
if ($incumplimientos.Count -gt 0) {
    $detalle = ($incumplimientos | ForEach-Object { "$($_.Ensayo) / $($_.Operacion), errores=$($_.Errores), p95=$($_.P95Ms) ms" }) -join '; '
    Write-Error "Criterio incumplido. $detalle"
}
