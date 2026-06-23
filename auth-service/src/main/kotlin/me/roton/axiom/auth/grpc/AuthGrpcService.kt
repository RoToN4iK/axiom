package me.roton.axiom.auth.grpc

import io.grpc.Status
import io.grpc.StatusRuntimeException
import me.roton.axiom.auth.domain.Account
import me.roton.axiom.auth.jwt.JwtService
import me.roton.axiom.auth.otp.OtpService
import me.roton.axiom.auth.repository.AccountRepository
import me.roton.axiom.contracts.auth.*
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthGrpcService(
    private val accountRepository: AccountRepository,
    private val passwordEncoder: BCryptPasswordEncoder,
    private val jwtService: JwtService,
    private val otpService: OtpService
) : AuthServiceGrpcKt.AuthServiceCoroutineImplBase() {

    override suspend fun registerAccount(request: RegisterAccountRequest): RegisterAccountResponse {
        validateEmailAndPassword(request.email, request.password)

        if (accountRepository.findByEmail(request.email) != null) {
            throw StatusRuntimeException(
                Status.ALREADY_EXISTS.withDescription("Account with email ${request.email} already exists")
            )
        }

        val account = Account(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)!!
        )
        val saved = accountRepository.save(account)

        return registerAccountResponse {
            accountId = saved.id.toString()
            email = saved.email
        }
    }

    override suspend fun login(request: LoginRequest): LoginResponse {
        val account = accountRepository.findByEmail(request.email)
            ?: throw StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("Invalid email or password"))

        if (!passwordEncoder.matches(request.password, account.passwordHash)) {
            // deliberately the SAME error message as "account not found" above —
            // never reveal whether the email exists or the password was wrong
            throw StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("Invalid email or password"))
        }

        val tokens = jwtService.generateTokenPair(account.id!!, account.role)

        return loginResponse {
            accessToken = tokens.accessToken
            refreshToken = tokens.refreshToken
        }
    }

    override suspend fun requestOtp(request: RequestOtpRequest): RequestOtpResponse {
        val account = accountRepository.findByEmail(request.email)
            ?: throw StatusRuntimeException(Status.NOT_FOUND.withDescription("No account with that email"))

        val code = otpService.generateAndStore(account.email)

        // TODO: actually send this via notification-service once it exists.
        // For now, this is a stand-in so the RPC is testable end-to-end.
        println("OTP for ${account.email}: $code")

        return requestOtpResponse {
            sent = true
        }
    }

    override suspend fun verifyOtp(request: VerifyOtpRequest): VerifyOtpResponse {
        val isValid = otpService.verify(request.email, request.code)

        return verifyOtpResponse {
            valid = isValid
        }
    }

    override suspend fun refreshToken(request: RefreshTokenRequest): RefreshTokenResponse {
        val accountId = jwtService.validateAndExtractAccountId(request.refreshToken, expectedType = "refresh")
            ?: throw StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("Invalid or expired refresh token"))

        val account = accountRepository.findById(accountId).orElseThrow {
            StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("Account no longer exists"))
        }

        val tokens = jwtService.generateTokenPair(account.id!!, account.role)

        return refreshTokenResponse {
            accessToken = tokens.accessToken
            refreshToken = tokens.refreshToken
        }
    }

    private fun validateEmailAndPassword(email: String, password: String) {
        if (email.isBlank() || !email.contains("@")) {
            throw StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Invalid email"))
        }
        if (password.length < 8) {
            throw StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Password must be at least 8 characters"))
        }
    }
}