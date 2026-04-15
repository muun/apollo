package io.muun.apollo.domain.model.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ErrorReportIdTest {

    @Test
    fun sameThrowableInstance_producesSameUniqueId() {
        val error = RuntimeException("boom")
        val report1 = ErrorReportBuilder.build(error)
        val report2 = ErrorReportBuilder.build(error)
        assertEquals(report1.uniqueId, report2.uniqueId)
    }

    @Test
    fun differentThrowableInstances_produceDifferentUniqueIds() {
        val report1 = ErrorReportBuilder.build(RuntimeException("boom"))
        val report2 = ErrorReportBuilder.build(RuntimeException("boom"))
        assertNotEquals(report1.uniqueId, report2.uniqueId)
    }

    @Test
    fun sameThrowableWithTagVariants_producesSameUniqueId() {
        val error = RuntimeException("boom")
        val report1 = ErrorReportBuilder.build("TagA", "msg", error)
        val report2 = ErrorReportBuilder.build(error)
        assertEquals(report1.uniqueId, report2.uniqueId)
    }
}
