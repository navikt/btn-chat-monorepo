package no.nav.btnchat.common.adt

sealed class Result<out SUCCESS, out FAILURE> {
    class Failure<out FAILURE>(val error: FAILURE) : Result<Nothing, FAILURE>()
    class Success<out SUCCESS>(val value: SUCCESS) : Result<SUCCESS, Nothing>()

    companion object {
        fun <FAILURE> failure(error: FAILURE): Result<Nothing, FAILURE>  = Failure(error)
        fun <SUCCESS> success(value: SUCCESS): Result<SUCCESS, Nothing>  = Success(value)

        fun <SUCCESS, FAILURE> ofNotNull(value: SUCCESS?, error: FAILURE): Result<SUCCESS, FAILURE> =
                if (value != null) {
                    Success(value)
                } else {
                    Failure(error)
                }
    }
}
