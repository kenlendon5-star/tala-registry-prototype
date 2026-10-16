package ph.tala.registry.domain.form

import org.junit.Assert.*
import org.junit.Test
import ph.tala.registry.domain.model.*
import java.time.LocalDate

/**
 * Field specifications and validation for the six household-level service sections.
 * Conditional fields need their parent answers in the state to be visible.
 */
class HouseholdServicesValidationTest {

    private val today = LocalDate.of(2026, 10, 9)
    private fun value(text: String) = AnswerValue.Scalar(text)
    private fun error(field: FieldSpec, text: String, vararg parents: Pair<String, String>) =
        FormRules.error(field, parents.associate { it.first to AnswerValue.Scalar(it.second) } + (field.key to value(text)), today)

    @Test fun `all six sections are household scoped with unique field keys`() {
        val sections = listOf(
            QuestionnaireSections.health,
            QuestionnaireSections.food,
            QuestionnaireSections.transport,
            QuestionnaireSections.internet,
            QuestionnaireSections.water,
            QuestionnaireSections.housing,
        )
        for (section in sections) {
            assertFalse("${section.route} must not be member-scoped", section.scopedToMember)
            val keys = section.fields.map { it.key }
            assertEquals("${section.route} field keys must be unique", keys.size, keys.distinct().size)
            assertTrue("${section.route} must declare fields", keys.isNotEmpty())
        }
    }

    @Test fun `health validation and conditional treatment`() {
        val health = QuestionnaireSections.health
        val illness = health.fields.first { it.key == "illness" }
        val treatment = health.fields.first { it.key == "treatment" }
        val healthAccess = health.fields.first { it.key == "healthAccess" }

        assertNotNull("illness is required", error(illness, ""))
        assertNull(error(illness, "Yes"))
        assertNull(error(illness, "No"))

        // treatment visible only when illness = Yes
        assertNotNull("treatment is required when visible", error(treatment, "", "illness" to "Yes"))
        assertNull(error(treatment, "Barangay health center", "illness" to "Yes"))
        assertNull("treatment stays valid when hidden", error(treatment, "", "illness" to "No"))
        assertNotNull("treatment rejects off-list answers", error(treatment, "Faith healer", "illness" to "Yes"))

        assertNotNull("healthAccess is required", error(healthAccess, ""))
        assertNull(error(healthAccess, "Yes"))
        assertNull(error(healthAccess, "No"))
    }

    @Test fun `food security validation`() {
        val food = QuestionnaireSections.food
        val foodWorry = food.fields.first { it.key == "foodWorry" }
        val skippedMeals = food.fields.first { it.key == "skippedMeals" }
        val foodAssistance = food.fields.first { it.key == "foodAssistance" }

        assertNotNull("foodWorry is required", error(foodWorry, ""))
        assertNull(error(foodWorry, "Never"))
        assertNull(error(foodWorry, "Often"))
        assertNotNull("foodWorry rejects off-list answers", error(foodWorry, "Always"))

        for (field in listOf(skippedMeals, foodAssistance)) {
            assertNotNull("${field.key} is required", error(field, ""))
            assertNull(error(field, "Yes"))
            assertNull(error(field, "No"))
            assertNotNull("${field.key} rejects off-list answers", error(field, "Maybe"))
        }
    }

    @Test fun `transport multi select validation and exclusivity`() {
        val transport = QuestionnaireSections.transport
        val transportField = transport.fields.first { it.key == "transport" }
        val accounts = transport.fields.first { it.key == "accounts" }

        assertNotNull("transport is required", error(transportField, ""))
        assertNull(error(transportField, "Tricycle"))
        assertNotNull("transport rejects off-list answers", error(transportField, "Hovercraft"))

        fun accountsError(selected: List<String>) =
            FormRules.error(accounts, mapOf("accounts" to AnswerValue.Multi(selected)), today)

        assertNotNull("accounts is required", accountsError(emptyList()))
        assertNull(accountsError(listOf("Bank")))
        assertNull(accountsError(listOf("Bank", "E-money", "Cooperative")))
        assertNull(accountsError(listOf("None")))
        assertNull(accountsError(listOf("Prefer not to answer")))
        assertNotNull("None cannot coexist with other selections", accountsError(listOf("Bank", "None")))
        assertNotNull("Prefer not to answer cannot coexist with other selections",
            accountsError(listOf("E-money", "Prefer not to answer")))
        assertNotNull("accounts rejects off-list options", accountsError(listOf("Under the mattress")))
        assertNotNull("accounts rejects duplicate selections", accountsError(listOf("Bank", "Bank")))
    }

    @Test fun `internet validation and conditional connection`() {
        val internet = QuestionnaireSections.internet
        val internetField = internet.fields.first { it.key == "internet" }
        val connection = internet.fields.first { it.key == "connection" }
        val safety = internet.fields.first { it.key == "safety" }

        assertNotNull("internet is required", error(internetField, ""))
        assertNull(error(internetField, "Yes"))

        assertNotNull("connection is required when visible", error(connection, "", "internet" to "Yes"))
        assertNull(error(connection, "Mobile data", "internet" to "Yes"))
        assertNull("connection stays valid when hidden", error(connection, "", "internet" to "No"))
        assertNotNull("connection rejects off-list answers", error(connection, "Carrier pigeon", "internet" to "Yes"))

        assertNotNull("safety is required", error(safety, ""))
        assertNull(error(safety, "Very safe"))
        assertNull("prefer-not-to-answer is a valid safety answer", error(safety, "Prefer not to answer"))
        assertNotNull("safety rejects off-list answers", error(safety, "Safe enough"))
    }

    @Test fun `water sanitation validation`() {
        val water = QuestionnaireSections.water
        val waterField = water.fields.first { it.key == "water" }
        val toilet = water.fields.first { it.key == "toilet" }
        val waste = water.fields.first { it.key == "waste" }
        val handwashing = water.fields.first { it.key == "handwashing" }

        assertNotNull("water source is required", error(waterField, ""))
        assertNull(error(waterField, "Piped into dwelling"))
        assertNotNull("water source rejects off-list answers", error(waterField, "Dew"))

        assertNotNull("toilet is required", error(toilet, ""))
        assertNull(error(toilet, "Flush / pour flush"))
        assertNull("no toilet is a valid recorded answer", error(toilet, "No toilet"))
        assertNotNull("toilet rejects off-list answers", error(toilet, "Bush"))

        fun wasteError(selected: List<String>) =
            FormRules.error(waste, mapOf("waste" to AnswerValue.Multi(selected)), today)
        assertNotNull("waste is required", wasteError(emptyList()))
        assertNull(wasteError(listOf("Collection service")))
        assertNull(wasteError(listOf("Composting", "Recycling", "Burning")))
        assertNotNull("waste rejects off-list options", wasteError(listOf("Landfill")))

        assertNotNull("handwashing is required", error(handwashing, ""))
        assertNull(error(handwashing, "Yes"))
        assertNull(error(handwashing, "No"))
    }

    @Test fun `housing validation with numeric room limits`() {
        val housing = QuestionnaireSections.housing
        val roof = housing.fields.first { it.key == "roof" }
        val walls = housing.fields.first { it.key == "walls" }
        val tenure = housing.fields.first { it.key == "tenure" }
        val electricity = housing.fields.first { it.key == "electricity" }
        val rooms = housing.fields.first { it.key == "rooms" }

        for (field in listOf(roof, walls, tenure)) {
            assertNotNull("${field.key} is required", error(field, ""))
            assertNotNull("${field.key} rejects off-list answers", error(field, "Invalid"))
        }
        assertNull(error(roof, "Metal roofing sheets"))
        assertNull(error(walls, "Concrete / brick"))
        assertNull(error(tenure, "Owned"))

        assertNotNull("electricity is required", error(electricity, ""))
        assertNull(error(electricity, "Yes"))
        assertNull(error(electricity, "No"))

        assertNotNull("rooms is required", error(rooms, ""))
        assertNotNull("rooms must be a whole number", error(rooms, "2.5"))
        assertNotNull("rooms rejects non-numeric text", error(rooms, "many"))
        assertNotNull("rooms cannot be negative", error(rooms, "-1"))
        assertNull(error(rooms, "0"))
        assertNull(error(rooms, "3"))
        assertNotNull("rooms cannot exceed the limit", error(rooms, "51"))
        assertNull(error(rooms, "50"))
    }

    @Test fun `validate reports only visible household fields`() {
        val health = QuestionnaireSections.health
        // illness answered, treatment hidden: validation must not flag the hidden field.
        val errors = FormRules.validate(health, mapOf("illness" to value("Yes")), today)
        assertTrue("treatment required once visible", "treatment" in errors)
        assertTrue("illness answered", "illness" !in errors)

        val hidden = FormRules.validate(health, emptyMap(), today)
        assertEquals("only unconditional required fields flagged while treatment hidden", setOf("illness", "healthAccess"), hidden.keys)
    }
}
