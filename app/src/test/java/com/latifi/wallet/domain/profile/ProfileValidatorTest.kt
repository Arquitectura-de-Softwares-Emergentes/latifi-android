package com.latifi.wallet.domain.profile

import com.latifi.wallet.domain.model.ContactType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {
    @Test
    fun nameBoundariesAreZeroOneEightyAndEightyOne() {
        assertEquals(NameError.Required, ProfileValidator.validateName(""))
        assertNull(ProfileValidator.validateName("A"))
        assertNull(ProfileValidator.validateName("A".repeat(80)))
        assertEquals(NameError.TooLong, ProfileValidator.validateName("A".repeat(81)))
    }

    @Test
    fun aBlankNameIsRequiredAfterTheTrim() {
        assertEquals(NameError.Required, ProfileValidator.validateName("   \t "))
    }

    @Test
    fun theNameIsTrimmedBeforeItsLengthIsCounted() {
        assertNull(ProfileValidator.validateName("  " + "A".repeat(80) + "  "))
        assertEquals(NameError.TooLong, ProfileValidator.validateName(" " + "A".repeat(81) + " "))
    }

    @Test
    fun contactLengthBoundariesAreTwoThreeTwoFiftyFourAndTwoFiftyFive() {
        // Too short for any type, whatever the pattern says.
        assertEquals(ContactError.Invalid, ProfileValidator.validateContact(ContactType.EMAIL, "a@"))
        // 3 characters is the lowest length, but "a@b" has no dot in the domain.
        assertEquals(ContactError.Invalid, ProfileValidator.validateContact(ContactType.EMAIL, "a@b"))
        val local = "a".repeat(254 - "@x.co".length)
        assertEquals(254, (local + "@x.co").length)
        assertNull(ProfileValidator.validateContact(ContactType.EMAIL, local + "@x.co"))
        assertEquals(ContactError.Invalid, ProfileValidator.validateContact(ContactType.EMAIL, "a" + local + "@x.co"))
    }

    @Test
    fun validEmailsPass() {
        listOf("name@example.com", "a.b+c@sub.domain.org", "x@y.io", "  padded@example.com  ").forEach {
            assertNull(it, ProfileValidator.validateContact(ContactType.EMAIL, it))
        }
    }

    @Test
    fun invalidEmailsFail() {
        listOf("plainaddress", "@example.com", "name@", "name@example", "na me@example.com", "a@@b.com", "a@b..com", "a@.com")
            .forEach { assertEquals(it, ContactError.Invalid, ProfileValidator.validateContact(ContactType.EMAIL, it)) }
    }

    @Test
    fun aPhoneNeedsTheLeadingPlus() {
        assertNull(ProfileValidator.validateContact(ContactType.PHONE, "+51987654321"))
        assertEquals(ContactError.Invalid, ProfileValidator.validateContact(ContactType.PHONE, "51987654321"))
    }

    @Test
    fun aPhoneNeedsSevenToFifteenDigits() {
        assertEquals(ContactError.Invalid, ProfileValidator.validateContact(ContactType.PHONE, "+123456"))
        assertNull(ProfileValidator.validateContact(ContactType.PHONE, "+1234567"))
        assertNull(ProfileValidator.validateContact(ContactType.PHONE, "+" + "1".repeat(15)))
        assertEquals(ContactError.Invalid, ProfileValidator.validateContact(ContactType.PHONE, "+" + "1".repeat(16)))
    }

    @Test
    fun aPhoneMayHaveSpacesDashesAndParentheses() {
        assertNull(ProfileValidator.validateContact(ContactType.PHONE, "+51 (987) 654-321"))
        assertEquals(ContactError.Invalid, ProfileValidator.validateContact(ContactType.PHONE, "+51 987 abc 321"))
    }

    @Test
    fun theTypeDecidesWhichPatternApplies() {
        assertEquals(ContactError.Invalid, ProfileValidator.validateContact(ContactType.PHONE, "name@example.com"))
        assertEquals(ContactError.Invalid, ProfileValidator.validateContact(ContactType.EMAIL, "+51987654321"))
    }

    @Test
    fun validateReportsEachFieldAndTheCount() {
        val none = ProfileValidator.validate("Ana", ContactType.EMAIL, "ana@example.com")
        assertTrue(none.isValid)
        assertEquals(0, none.count)

        val both = ProfileValidator.validate("", ContactType.PHONE, "123")
        assertFalse(both.isValid)
        assertEquals(2, both.count)
        assertEquals(NameError.Required, both.name)
        assertEquals(ContactError.Invalid, both.contact)

        val one = ProfileValidator.validate("Ana", ContactType.EMAIL, "nope")
        assertEquals(1, one.count)
        assertNull(one.name)
    }
}
