package me.roton.axiom.subscription.config

import me.roton.axiom.common.time.Clock
import me.roton.axiom.common.time.SystemClock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = SystemClock()
}