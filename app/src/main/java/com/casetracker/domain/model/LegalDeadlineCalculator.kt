package com.casetracker.domain.model

object LegalDeadlineCalculator {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    // 交通事故认定书：现场调查之日起 10 日
    fun accidentCertificateDeadline(investigationDate: Long): Long =
        investigationDate + 10 * DAY_MS

    // 检验鉴定后：结论确定之日起 5 日
    fun certificateAfterAppraisalDeadline(appraisalConclusionDate: Long): Long =
        appraisalConclusionDate + 5 * DAY_MS

    // 复核申请：送达之日起 3 日
    fun reviewApplicationDeadline(certificateDeliveryDate: Long): Long =
        certificateDeliveryDate + 3 * DAY_MS

    // 复核结论：受理之日起 30 日
    fun reviewConclusionDeadline(reviewAcceptDate: Long): Long =
        reviewAcceptDate + 30 * DAY_MS

    // 违法一般程序：立案之日起 30 日
    fun violationGeneralDeadline(filingDate: Long): Long =
        filingDate + 30 * DAY_MS

    fun calculateDeadline(caseType: CaseType, status: CaseStatus, referenceDate: Long): Long {
        return when (caseType) {
            CaseType.ACCIDENT -> when (status) {
                CaseStatus.UNDER_INVESTIGATION -> accidentCertificateDeadline(referenceDate)
                CaseStatus.IN_PROGRESS -> certificateAfterAppraisalDeadline(referenceDate)
                CaseStatus.REVIEW -> reviewConclusionDeadline(referenceDate)
                else -> referenceDate + 30 * DAY_MS
            }
            CaseType.VIOLATION -> when (status) {
                CaseStatus.UNDER_INVESTIGATION, CaseStatus.IN_PROGRESS ->
                    violationGeneralDeadline(referenceDate)
                else -> referenceDate + 30 * DAY_MS
            }
        }
    }
}
