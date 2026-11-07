package app.xarena.nativeapp

/** The patch path never calls the full downloader unless that larger transfer was selected. */
internal object UpdateTransfer {
    data class Result(val error: String?, val needsFullConsent: Boolean)
    suspend fun run(hasPatch: Boolean, fullApproved: Boolean,
                    patchAttempt: suspend () -> String?, fullAttempt: suspend () -> String?): Result {
        if (hasPatch && !fullApproved) {
            val error = patchAttempt()
            return Result(error, error != null)
        }
        return Result(fullAttempt(), false)
    }
}
