package com.ecoachsolutions.ecoachbooks.Helpers

import com.ecoachsolutions.ecoachbooks.BuildConfig

object MasterSettings {

    private val TargetEnvironment = if (BuildConfig.DEBUG) EcEnvironmentTarget.DEV else EcEnvironmentTarget.PROD

    fun isDevMode(): Boolean {
        return TargetEnvironment == EcEnvironmentTarget.DEV
    }

    fun isStagingMode(): Boolean {
        return TargetEnvironment == EcEnvironmentTarget.STAGING
    }

    fun isProductionMode(): Boolean {
        return TargetEnvironment == EcEnvironmentTarget.PROD
    }
}
