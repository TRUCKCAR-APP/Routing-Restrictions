package escuelaing.edu.co.truckdar.routing_restrictions.dto.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {

    @Test
    void serializesRestrictionCreatedEnvelope() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        EventEnvelope<RoadRestrictionEvent> envelope = EventEnvelope.<RoadRestrictionEvent>builder()
                .eventId(eventId)
                .eventType("restriction.created")
                .occurredAt(Instant.parse("2026-10-10T23:00:00Z"))
                .correlationId(correlationId)
                .version(1)
                .producer("routing-restrictions")
                .payload(RoadRestrictionEvent.builder()
                        .restrictionId(42L)
                        .restrictionCode("RES-42")
                        .eventAction("CREATED")
                        .build())
                .build();

        EventEnvelope<RoadRestrictionEvent> restored = mapper.readValue(
                mapper.writeValueAsString(envelope),
                mapper.getTypeFactory().constructParametricType(
                        EventEnvelope.class,
                        RoadRestrictionEvent.class
                )
        );

        assertThat(restored.getEventId()).isEqualTo(eventId);
        assertThat(restored.getEventType()).isEqualTo("restriction.created");
        assertThat(restored.getOccurredAt()).isEqualTo(Instant.parse("2026-10-10T23:00:00Z"));
        assertThat(restored.getCorrelationId()).isEqualTo(correlationId);
        assertThat(restored.getPayload().getRestrictionId()).isEqualTo(42L);
    }
}
