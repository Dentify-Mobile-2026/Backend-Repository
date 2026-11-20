package com.platform.dentify.invoices.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InvoiceSchemaVerifierTest {

    private JdbcTemplate jdbcTemplate;
    private InvoiceSchemaVerifier verifier;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        verifier = new InvoiceSchemaVerifier(jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    private void givenRules(List<InvoiceSchemaVerifier.ForeignKeyRule> rules) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn((List) rules);
    }

    @Test
    void cascadeRuleIsReportedAsUnsafe() {
        givenRules(List.of(new InvoiceSchemaVerifier.ForeignKeyRule("FKh2quh5xy8jiqbaxy6c9m8qe6d", "CASCADE")));

        var findings = verifier.verify();

        assertEquals(InvoiceSchemaVerifier.PROTECTED_FOREIGN_KEYS.size(), findings.size());
        assertFalse(findings.get(0).safe());
    }

    @Test
    void restrictOrNoActionIsSafe() {
        givenRules(List.of(new InvoiceSchemaVerifier.ForeignKeyRule("fk_invoice_appointment", "RESTRICT")));
        assertTrue(verifier.verify().get(0).safe());

        givenRules(List.of(new InvoiceSchemaVerifier.ForeignKeyRule("fk_invoice_appointment", "NO ACTION")));
        assertTrue(verifier.verify().get(0).safe());
    }

    @Test
    void duplicatedForeignKeysAreUnsafeIfAnyOfThemCascades() {
        givenRules(List.of(
                new InvoiceSchemaVerifier.ForeignKeyRule("fk_invoice_appointment", "RESTRICT"),
                new InvoiceSchemaVerifier.ForeignKeyRule("FKh2quh5xy8jiqbaxy6c9m8qe6d", "CASCADE")));

        assertFalse(verifier.verify().get(0).safe());
    }

    @Test
    void missingForeignKeyIsUnsafe() {
        givenRules(List.of());

        assertFalse(verifier.verify().get(0).safe());
    }

    @Test
    void verifierOnlyReadsAndNeverRunsDdl() {
        givenRules(List.of(new InvoiceSchemaVerifier.ForeignKeyRule("FKh2quh5xy8jiqbaxy6c9m8qe6d", "CASCADE")));

        verifier.verify();

        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).update(anyString());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void databaseErrorsDoNotPreventStartup() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("no access to information_schema"));

        assertTrue(verifier.verify().isEmpty());
    }
}
