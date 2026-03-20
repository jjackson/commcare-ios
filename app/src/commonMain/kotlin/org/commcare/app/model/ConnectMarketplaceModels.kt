package org.commcare.app.model

import org.commcare.app.platform.currentEpochSeconds

/**
 * Returns the approximate number of days until the given date string ("YYYY-MM-DD").
 * Returns Int.MAX_VALUE if the string is null or unparseable.
 * Uses a rough epoch-based calculation suitable for "< 5 days" warning UI.
 */
fun daysUntil(dateStr: String?): Int {
    if (dateStr == null) return Int.MAX_VALUE
    return try {
        val parts = dateStr.split("-")
        if (parts.size != 3) return Int.MAX_VALUE
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        // Rough calendar-to-days since epoch (Julian Day approximation)
        val targetDays = year * 365 + month * 30 + day
        val nowSeconds = currentEpochSeconds()
        val currentDays = (nowSeconds / 86400).toInt() + 719163
        targetDays - currentDays
    } catch (_: Exception) {
        Int.MAX_VALUE
    }
}

// Matches OpportunitySerializer exactly
data class Opportunity(
    val id: Int,
    val opportunityId: String,  // UUID string
    val name: String,
    val description: String,
    val shortDescription: String?,
    val organization: String,   // org slug
    val learnApp: CommCareAppInfo?,
    val deliverApp: CommCareAppInfo?,
    val startDate: String?,     // "2026-03-01" date string
    val endDate: String?,
    val maxVisitsPerUser: Int,  // -1 if null
    val dailyMaxVisitsPerUser: Int, // -1 if null
    val budgetPerVisit: Int,    // -1 if null
    val totalBudget: Long?,
    val claim: OpportunityClaim?, // null if not claimed
    val learnProgress: LearnProgressSummary?,
    val deliverProgress: Int,   // visit count
    val currency: String?,
    val isActive: Boolean,
    val budgetPerUser: Int,
    val paymentUnits: List<PaymentUnit>,
    val isUserSuspended: Boolean,
    val verificationFlags: VerificationFlags?,
    val catchmentAreas: List<CatchmentArea>
) {
    val isClaimed: Boolean get() = claim != null
    val daysRemaining: Int get() = daysUntil(endDate)
}

data class CommCareAppInfo(
    val id: Int,
    val ccDomain: String,
    val ccAppId: String,
    val name: String,
    val description: String,
    val organization: String,
    val learnModules: List<LearnModuleInfo>,
    val passingScore: Int,  // -1 if null
    val installUrl: String?
)

data class LearnModuleInfo(
    val id: Int,
    val slug: String,
    val name: String,
    val description: String,
    val timeEstimate: Int  // hours
)

data class OpportunityClaim(
    val id: Int,
    val maxPayments: Int,   // -1 if null
    val endDate: String?,
    val dateClaimed: String?,
    val paymentUnits: List<ClaimPaymentUnit>
)

data class ClaimPaymentUnit(
    val maxVisits: Int,
    val paymentUnit: Int,       // FK id
    val paymentUnitId: String   // UUID
)

data class PaymentUnit(
    val id: Int,
    val paymentUnitId: String,  // UUID
    val name: String,
    val maxTotal: Int?,
    val maxDaily: Int?,
    val amount: Int,            // per-visit amount (integer)
    val endDate: String?
)

// Summary on opportunity list (inline, not a separate endpoint)
data class LearnProgressSummary(
    val totalModules: Int,
    val completedModules: Int
)

// From /learn_progress endpoint
data class LearnProgressDetail(
    val completedModules: List<CompletedModule>,
    val assessments: List<Assessment>
)

data class CompletedModule(
    val id: Int,
    val module: Int,    // FK to LearnModule.id
    val date: String,
    val duration: String?
)

data class Assessment(
    val id: Int,
    val date: String,
    val score: Int,
    val passingScore: Int,
    val passed: Boolean
)

// From /delivery_progress endpoint
data class DeliveryProgressDetail(
    val deliveries: List<DeliveryRecord>,
    val payments: List<PaymentRecord>,
    val maxPayments: Int,
    val paymentAccrued: Int,
    val endDate: String?
)

data class DeliveryRecord(
    val id: Int,
    val status: String,         // "pending", "approved", "rejected", "over_limit", "incomplete"
    val visitDate: String?,
    val deliverUnitName: String?,
    val deliverUnitSlug: String?,   // payment_unit PK as string
    val deliverUnitSlugId: String?, // payment_unit UUID
    val entityId: String?,
    val entityName: String?,
    val reason: String?,
    val flags: Map<String, String>, // slug -> reason
    val lastModified: String?
)

data class PaymentRecord(
    val id: Int,
    val paymentId: String,      // UUID
    val amount: String,         // decimal string "10.00"
    val datePaid: String?,
    val confirmed: Boolean,
    val confirmationDate: String?
)

data class VerificationFlags(
    val formSubmissionStart: String?, // "10:00:00"
    val formSubmissionEnd: String?    // "14:00:00"
)

data class CatchmentArea(
    val id: Int,
    val name: String,
    val latitude: String,
    val longitude: String,
    val radius: Int,
    val active: Boolean
)

// Messaging models (on ConnectID server, not commcare-connect)
data class MessageThread(
    val id: String,
    val participantName: String,
    val lastMessage: String,
    val lastMessageDate: String,
    val unreadCount: Int
)

data class Message(
    val id: String,
    val threadId: String,
    val senderName: String,
    val content: String,
    val timestamp: String,
    val isFromMe: Boolean
)
