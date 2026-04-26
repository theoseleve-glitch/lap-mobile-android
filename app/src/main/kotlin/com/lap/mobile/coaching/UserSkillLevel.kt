package com.lap.mobile.coaching

/**
 * Driver self-reported experience tier (T221) — drives the
 * coaching prompt's explanation variant when a rule ships
 * tier-specific body text.
 *
 * Three tiers chosen to match the typical track-day population:
 *   - [BEGINNER]:   first-year track driver. Bodies focus on
 *     reference points and observable behaviors ("brake earlier
 *     — try the 100m board").
 *   - [INTERMEDIATE]: 1-3 years of track time. Bodies use
 *     cornering vocabulary without assuming racecraft expertise
 *     ("try braking 30m earlier to carry more apex speed").
 *   - [EXPERT]:     instructor / regional racer. Bodies include
 *     trail-braking %s, weight-transfer language, and assume
 *     comfort with all standard race terms.
 *
 * Default = [INTERMEDIATE]. Two reasons:
 *   1. Most users self-categorize as intermediate when first
 *      asked (the Dunning-Kruger middle).
 *   2. Intermediate body is the safest fallback when a rule's
 *      [`bodies`] JSON object is missing the user's actual tier
 *      — falling back to the broadest-applicable explanation
 *      keeps the coaching useful rather than dropping the tip.
 *
 * Wire format mapping (lower-case tier name → key in the rule's
 * `bodies` object): see [parseInsightPromptPayload]. Server
 * authoring tool emits keys as lowercase per the contract here;
 * future i18n can extend with locale-tagged keys
 * (`"intermediate.fr"`, etc.) without changing this enum.
 */
enum class UserSkillLevel {
    BEGINNER,
    INTERMEDIATE,
    EXPERT,

    ;

    companion object {
        val DEFAULT: UserSkillLevel = INTERMEDIATE
    }
}
