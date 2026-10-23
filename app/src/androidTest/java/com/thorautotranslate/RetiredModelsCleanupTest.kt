package com.thorautotranslate

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

class RetiredModelsCleanupTest {
    @Test fun removeExperimentalDownloadsFromDevelopmentDevice() {
        cleanupRetiredModels(InstrumentationRegistry.getInstrumentation().targetContext)
    }
}
