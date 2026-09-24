package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.Contribution
import com.erfanbagheri.controlix.data.ContributionBundle
import com.erfanbagheri.controlix.data.ContributionField
import com.erfanbagheri.controlix.data.ContributionRejection
import com.erfanbagheri.controlix.data.ContributionRejectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic shape fixtures only: these are not device codes, they exercise
 * the validator/serialiser boundary. A real contribution must come from a
 * known, permissively licensed source with stated provenance.
 */
private const val SHAPE_PATTERN = "900 901 902 903 904 905 906 907 908 909 910 911 912 913 914 915"

private fun sample(
    brand: String = "Sony",
    category: String = "tvs",
    remoteName: String = "RM-ED009",
    buttonName: String = "Power",
    carrierHz: Int = 38000,
    pattern: String = SHAPE_PATTERN,
    provenance: String = "Public irdb dump; retested on the owner's own remote",
    appVersion: String = "0.1.0",
) = Contribution(brand, category, remoteName, buttonName, carrierHz, pattern, provenance, appVersion)

class ContributionValidationTest {

    @Test
    fun completeContributionWithKnownProvenanceIsAccepted() {
        assertEquals(emptyList<ContributionRejection>(), sample().validate())
    }

    @Test
    fun blankRequiredFieldsReturnOneTypedReasonPerField() {
        val reasons = sample(
            brand = "  ", category = "", remoteName = " ", buttonName = "",
            provenance = "", appVersion = "",
        ).validate().associateBy { it.field }
        listOf(
            ContributionField.BRAND, ContributionField.CATEGORY, ContributionField.REMOTE_NAME,
            ContributionField.BUTTON_NAME, ContributionField.APP_VERSION,
        ).forEach { field ->
            val rejection = reasons.getValue(field)
            assertEquals("$field rejection type", ContributionRejectionType.REQUIRED, rejection.type)
            assertTrue("$field message must not be blank", rejection.message.isNotBlank())
        }
        // Blank provenance is its own legal reason, not a generic required check.
        assertEquals(
            ContributionRejectionType.PROVENANCE_MISSING,
            reasons.getValue(ContributionField.PROVENANCE).type,
        )
    }

    @Test
    fun oddTokenCountIsMalformedPattern() {
        val reasons = sample(pattern = "900 901 902 903 904 905 906").validate()
        assertEquals(
            listOf(ContributionRejectionType.PATTERN_ELEMENT_COUNT),
            reasons.map { it.type },
        )
        assertEquals(ContributionField.PATTERN, reasons.single().field)
    }

    @Test
    fun shortPatternIsRejectedAsMalformedElementCount() {
        val short = sample(pattern = "900 901")
        assertEquals(
            listOf(ContributionRejectionType.PATTERN_ELEMENT_COUNT),
            short.validate().map { it.type },
        )
    }

    @Test
    fun nonNumericTokenIsMalformedPattern() {
        val bad = sample(pattern = SHAPE_PATTERN.replace("906", "six"))
        val reasons = bad.validate()
        assertEquals(
            listOf(ContributionRejectionType.PATTERN_NON_NUMERIC),
            reasons.map { it.type },
        )
        assertTrue(reasons.single().message.contains("six"))
    }

    @Test
    fun implausibleDurationIsRejected() {
        val bad = sample(pattern = "900 0 902 903 904 905 906 907 908 909 910 911 912 913 914 915")
        val reasons = bad.validate()
        assertEquals(
            listOf(ContributionRejectionType.PATTERN_DURATION_RANGE),
            reasons.map { it.type },
        )
    }

    @Test
    fun zeroCarrierIsMissingNotImplausible() {
        val reasons = sample(carrierHz = 0).validate()
        assertEquals(
            listOf(ContributionRejectionType.CARRIER_MISSING),
            reasons.map { it.type },
        )
        assertEquals(ContributionField.CARRIER, reasons.single().field)
    }

    @Test
    fun outOfRangeCarrierIsImplausible() {
        listOf(5000, 250000).forEach { hz ->
            val reasons = sample(carrierHz = hz).validate()
            assertEquals("carrier $hz", listOf(ContributionRejectionType.CARRIER_IMPLAUSIBLE), reasons.map { it.type })
        }
    }

    @Test
    fun blankProvenanceIsRejected() {
        val reasons = sample(provenance = "   ").validate()
        assertEquals(
            listOf(ContributionRejectionType.PROVENANCE_MISSING),
            reasons.map { it.type },
        )
    }

    @Test
    fun unknownProvenanceIsRejectedAndSaysWhy() {
        val reasons = sample(provenance = "unknown").validate()
        assertEquals(
            listOf(ContributionRejectionType.PROVENANCE_UNKNOWN),
            reasons.map { it.type },
        )
        assertTrue(reasons.single().message.isNotBlank())
    }

    @Test
    fun proprietaryRemoteProvenanceIsRejectedAndNamesTheReason() {
        val reasons = sample(provenance = "Copied out of Mi Remote").validate()
        assertEquals(
            listOf(ContributionRejectionType.PROVENANCE_PROHIBITED),
            reasons.map { it.type },
        )
        val message = reasons.single().message
        assertTrue(message.contains("Mi Remote", ignoreCase = true))
        assertTrue(message.contains("proprietary", ignoreCase = true))
    }

    @Test
    fun validatorReportsEveryProblemInsteadOfThrowing() {
        val broken = sample(
            brand = "", category = "", remoteName = "", buttonName = "",
            carrierHz = -1, pattern = "", provenance = "Mi Remote", appVersion = " ",
        )
        val reasons = broken.validate()
        assertEquals(8, reasons.size)
        assertEquals(8, reasons.distinctBy { it.field }.size)
    }
}

class ContributionBundleTest {

    private val bundle = ContributionBundle(
        listOf(
            sample(),
            sample(buttonName = "Volume up"),
        )
    )

    @Test
    fun bundleSerializesToTheMaintainerJsonShape() {
        assertEquals(GOLDEN, bundle.toJson())
    }

    @Test
    fun goldenJsonRoundTripsThroughTheBundleParser() {
        val parsed = ContributionBundle.fromJson(GOLDEN)
        assertEquals(bundle, parsed)
        assertEquals(GOLDEN, parsed?.toJson())
    }

    @Test
    fun malformedJsonParsesToNullInsteadOfThrowing() {
        assertEquals(null, ContributionBundle.fromJson("{\"format\":"))
        assertEquals(null, ContributionBundle.fromJson("not json at all"))
    }

    @Test
    fun bundleValidationRejectsOneBadButtonInARemote() {
        val mixed = ContributionBundle(listOf(sample(), sample(buttonName = "", carrierHz = 0)))
        val reasons = mixed.validate()
        assertEquals(2, reasons.size)
        assertTrue(reasons.any { it.field == ContributionField.BUTTON_NAME })
        assertTrue(reasons.any { it.field == ContributionField.CARRIER })
    }

    @Test
    fun bundleValidationRejectsAProhibitedSourceForTheWholeRemote() {
        val reasons = ContributionBundle(listOf(sample(provenance = "Mi Remote"))).validate()
        assertTrue(reasons.any { it.type == ContributionRejectionType.PROVENANCE_PROHIBITED })
    }

    private companion object {
        const val GOLDEN = """{"format":"controlix-contribution-v1","brand":"Sony","category":"tvs","remoteName":"RM-ED009","provenance":"Public irdb dump; retested on the owner's own remote","appVersion":"0.1.0","buttons":[{"buttonName":"Power","carrierHz":38000,"pattern":"900 901 902 903 904 905 906 907 908 909 910 911 912 913 914 915"},{"buttonName":"Volume up","carrierHz":38000,"pattern":"900 901 902 903 904 905 906 907 908 909 910 911 912 913 914 915"}]}"""
    }
}