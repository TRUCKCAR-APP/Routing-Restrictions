package escuelaing.edu.co.truckcar.routing_restrictions.controller;

import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RoadRestrictionResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.exception.GlobalExceptionHandler;
import escuelaing.edu.co.truckcar.routing_restrictions.service.IRoadRestrictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Crear y desactivar restricciones viales: solo COORDINADOR o ADMIN. */
class RoadRestrictionControllerAccessTest {

    private static final String BODY = "{\"title\":\"Puente con gálibo de 4.2 m\",\"restrictionType\":\"GALIBO_ALTURA\","
            + "\"severity\":\"BLOQUEO_TOTAL\",\"roadCorridor\":\"BOGOTA-GIRARDOT\",\"maxClearanceMeters\":4.2}";

    private IRoadRestrictionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(IRoadRestrictionService.class);
        when(service.createRestriction(any())).thenReturn(RoadRestrictionResponse.builder().build());
        when(service.deactivateRestriction(anyLong())).thenReturn(RoadRestrictionResponse.builder().build());
        mvc = MockMvcBuilders.standaloneSetup(new RoadRestrictionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder create() {
        return post("/api/v1/restrictions").contentType(MediaType.APPLICATION_JSON).content(BODY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COORDINADOR", "ADMIN"})
    void controlTowerManagesRestrictions(String role) throws Exception {
        mvc.perform(create().header("X-Auth-Role", role)).andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/restrictions/3/deactivate").header("X-Auth-Role", role)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONDUCTOR", "GENERADOR"})
    void otherRolesGetForbidden(String role) throws Exception {
        mvc.perform(create().header("X-Auth-Role", role)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/restrictions/3/deactivate").header("X-Auth-Role", role)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void missingRoleIsUnauthenticated() throws Exception {
        mvc.perform(create()).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
