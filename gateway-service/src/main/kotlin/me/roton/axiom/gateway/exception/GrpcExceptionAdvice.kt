package me.roton.axiom.gateway.exception

import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/*
* Applies to every @RestController in this service, not just one — this is
* the entire reason @RestControllerAdvice exists over a plain per-controller @ExceptionHandler.
*/

@RestControllerAdvice
class GrpcExceptionAdvice {
    @ExceptionHandler(StatusRuntimeException::class)
    fun handleGrpcException(ex: StatusRuntimeException): ResponseEntity<String> {
        val httpStatus = when (ex.status.code) {
            Status.Code.INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST
            Status.Code.NOT_FOUND -> HttpStatus.NOT_FOUND
            Status.Code.ALREADY_EXISTS -> HttpStatus.CONFLICT
            Status.Code.FAILED_PRECONDITION -> HttpStatus.CONFLICT
            Status.Code.UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED
            Status.Code.PERMISSION_DENIED -> HttpStatus.FORBIDDEN
            Status.Code.UNIMPLEMENTED -> HttpStatus.NOT_IMPLEMENTED
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        return ResponseEntity.status(httpStatus).body(ex.status.description ?: "Unknown error")
    }
}