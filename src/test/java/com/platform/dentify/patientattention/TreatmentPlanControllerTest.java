package com.platform.dentify.patientattention;

import com.platform.dentify.patientattention.domain.services.*;
import com.platform.dentify.patientattention.interfaces.rest.TreatmentPlanController;
import com.platform.dentify.patientattention.domain.model.commands.CreateTreatmentPlanCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;

class TreatmentPlanControllerTest {
    private final TreatmentPlanCommandService commands = mock(TreatmentPlanCommandService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TreatmentPlanController(commands, mock(TreatmentPlanQueryService.class))).build();
    }

    @Test void decimalStringsAreDeserializedExactlyAndPassedToTheDomain() throws Exception {
        when(commands.handle(any(CreateTreatmentPlanCommand.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(post("/api/v1/patients/3/treatment-plans").contentType("application/json").content("""
                {"title":"Plan","items":[
                  {"treatmentId":4,"toothNumber":"11","agreedPrice":"120.50"},
                  {"treatmentId":4,"agreedPrice":"90.10"}]}
                """)).andExpect(status().isNotFound());
        var command = ArgumentCaptor.forClass(CreateTreatmentPlanCommand.class);
        verify(commands).handle(command.capture());
        assertEquals(new BigDecimal("120.50"), command.getValue().items().get(0).agreedPrice());
    }

    @Test void rejectsTooFewItemsAndInvalidPricesBeforeCallingTheService() throws Exception {
        for (String body : new String[]{
                "{\"title\":\"Plan\",\"items\":[]}",
                "{\"title\":\"Plan\",\"items\":[{\"treatmentId\":4,\"agreedPrice\":-1},{\"treatmentId\":4,\"agreedPrice\":1.001}]}",
                "{\"title\":\"Plan\",\"items\":[null,null]}"}) {
            mvc.perform(post("/api/v1/patients/3/treatment-plans").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(commands);
    }

    @Test void rejectsUnknownOrMissingItemStatus() throws Exception {
        for (String body : new String[]{"{}", "{\"status\":\"INVALID\"}"}) {
            mvc.perform(put("/api/v1/treatment-plans/1/items/2/status").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(commands);
    }
}
