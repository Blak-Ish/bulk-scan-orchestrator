package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.io.IOException;

public class EnvelopeParser {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeParser.class);

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    public static Envelope parse(String messageId, byte[] bytes) {
        try {
            Envelope envelope = objectMapper.readValue(bytes, Envelope.class);

            log.info(
                "Parsed message. ID: {}, Envelope ID: {}, Filename: {}, Jurisdiction: {}, Classification: {}, Case: {}",
                messageId,
                envelope.id,
                envelope.zipFileName,
                envelope.jurisdiction,
                envelope.classification,
                envelope.caseRef
            );

            return envelope;
        } catch (IOException exc) {
            throw new InvalidMessageException(exc);
        }
    }

    public static Envelope parse(String json) {
        return parse(json.getBytes());
    }

    private EnvelopeParser() {
        // utility class
    }
}
