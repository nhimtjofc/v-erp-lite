package base.payment;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentReferenceTest {

    @Test
    void issuedReferenceIsTwelveUppercaseCrockfordCharactersAndValidates() throws Exception {
        Class<?> referenceType = Class.forName("base.payment.PaymentReference");
        Method issue = referenceType.getMethod("issue", LocalDate.class);
        Method isValid = referenceType.getMethod("isValid", String.class);

        String reference = (String) issue.invoke(null, LocalDate.of(2026, 8, 21));

        assertEquals(12, reference.length());
        assertTrue(reference.matches("[0-9A-HJKMNPQRSTVWXYZ]{12}"));
        assertTrue((Boolean) isValid.invoke(null, reference));
    }

    @Test
    void rejectsAReferenceWhoseCheckCharacterWasChanged() throws Exception {
        Class<?> referenceType = Class.forName("base.payment.PaymentReference");
        Method issue = referenceType.getMethod("issue", LocalDate.class);
        Method isValid = referenceType.getMethod("isValid", String.class);

        String reference = (String) issue.invoke(null, LocalDate.of(2026, 8, 21));
        String altered = reference.substring(0, 11) + (reference.endsWith("0") ? "1" : "0");

        assertFalse((Boolean) isValid.invoke(null, altered));
    }
}
