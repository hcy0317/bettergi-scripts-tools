function Invoke-BetterGIScriptsToolsRetry {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Operation,

        [Parameter(Mandatory)]
        [scriptblock]$Action,

        [ValidateRange(1, 10)]
        [int]$MaxAttempts = 3,

        [ValidateRange(0, 300)]
        [int]$InitialDelaySeconds = 10,

        [ValidateRange(0, 300)]
        [int]$MaxDelaySeconds = 30,

        [scriptblock]$SleepAction = { param([int]$Seconds) Start-Sleep -Seconds $Seconds }
    )

    $lastExitCode = -1
    $lastException = $null
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        Write-Host "[$Operation] attempt $attempt/$MaxAttempts"
        try {
            $output = @(& $Action)
            if ($output.Count -eq 0) {
                $lastExitCode = 0
            }
            else {
                $lastExitCode = [int]$output[-1]
                if ($output.Count -gt 1) {
                    $output[0..($output.Count - 2)] | Write-Output
                }
            }
            $lastException = $null
        }
        catch {
            $lastExitCode = -1
            $lastException = $_.Exception
            Write-Warning "[$Operation] attempt $attempt failed: $($lastException.Message)"
        }

        if ($lastExitCode -eq 0) {
            Write-Host "[$Operation] succeeded on attempt $attempt."
            return
        }
        if ($attempt -eq $MaxAttempts) {
            $detail = if ($null -ne $lastException) {
                $lastException.Message
            }
            else {
                "exit code $lastExitCode"
            }
            throw "$Operation failed after $MaxAttempts attempts: $detail"
        }

        $delay = [Math]::Min(
            $MaxDelaySeconds,
            [int]($InitialDelaySeconds * [Math]::Pow(2, $attempt - 1)))
        Write-Warning "[$Operation] failed with exit code $lastExitCode; retrying in $delay seconds."
        if ($delay -gt 0) {
            & $SleepAction $delay
        }
    }
}
