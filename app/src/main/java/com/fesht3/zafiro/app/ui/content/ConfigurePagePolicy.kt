package com.fesht3.zafiro.app.ui.content

import com.fesht3.zafiro.app.ui.model.ProviderSpec

data class ConfigurePagePolicy(
    val showEndpointSection: Boolean,
    val showEndpointOverrideToggle: Boolean,
    val endpointEditable: Boolean,
)

internal fun onboardingConfigurePolicy(providerSpec: ProviderSpec): ConfigurePagePolicy {
    return ConfigurePagePolicy(
        showEndpointSection = providerSpec.allowsCustomEndpointInNewConfig,
        showEndpointOverrideToggle = providerSpec.allowsCustomEndpointInNewConfig,
        endpointEditable = providerSpec.allowsCustomEndpointInNewConfig,
    )
}
