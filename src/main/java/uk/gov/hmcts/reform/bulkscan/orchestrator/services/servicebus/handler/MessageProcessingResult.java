package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.MessageProcessingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.util.function.Function;
import java.util.function.Supplier;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.SUCCESS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.UNRECOVERABLE_FAILURE;

public class MessageProcessingResult {

    private static final Logger log = LoggerFactory.getLogger(MessageProcessingResult.class);

    final MessageProcessingResultType resultType;

    private final Envelope envelope;

    public final Exception exception;

    private MessageProcessingResult(MessageProcessingResultType resultType, Envelope envelope) {
        this(resultType, envelope, null);
    }

    private MessageProcessingResult(MessageProcessingResultType resultType, Exception exception) {
        this(resultType, null, exception);
    }

    private MessageProcessingResult(MessageProcessingResultType resultType, Envelope envelope, Exception exception) {
        this.resultType = resultType;
        this.envelope = envelope;
        this.exception = exception;
    }

    static MessageProcessingResult success(Envelope envelope) {
        return new MessageProcessingResult(SUCCESS, envelope);
    }

    static MessageProcessingResult recoverable(Exception exception) {
        return new MessageProcessingResult(POTENTIALLY_RECOVERABLE_FAILURE, exception);
    }

    static MessageProcessingResult recoverable(Envelope envelope, Exception exception) {
        return new MessageProcessingResult(POTENTIALLY_RECOVERABLE_FAILURE, envelope, exception);
    }

    static MessageProcessingResult unrecoverable(Exception exception) {
        return new MessageProcessingResult(UNRECOVERABLE_FAILURE, exception);
    }

    static MessageProcessingResult unrecoverable(Envelope envelope, Exception exception) {
        return new MessageProcessingResult(UNRECOVERABLE_FAILURE, envelope, exception);
    }

    private boolean isSuccess() {
        return MessageProcessingResultType.SUCCESS.equals(resultType);
    }

    MessageProcessingResult andThen(Supplier<MessageProcessingResult> function) {
        return isSuccess() ? function.get() : this;
    }

    MessageProcessingResult andThenWithEnvelope(Function<Envelope, MessageProcessingResult> function) {
        return andThen(() -> function.apply(envelope));
    }

    MessageProcessingResult logProcessFinish(String messageId) {
        switch (resultType) {
            case SUCCESS:
                if (envelope != null) {
                    log.info("Processed message with ID {}. File name: {}", messageId, envelope.zipFileName);
                }

                break;
            case POTENTIALLY_RECOVERABLE_FAILURE:
            case UNRECOVERABLE_FAILURE:
                log.error(getProcessFinishMessage(messageId), exception);

                break;
            default:
                throw new MessageProcessingException(
                    "Unknown message processing result type: " + resultType
                );
        }

        return this;
    }

    private String getProcessFinishMessage(String messageId) {
        if (exception instanceof InvalidMessageException) {
            return String.format("Rejected message with ID %s, because it's invalid", messageId);
        } else {
            String baseMessage = String.format("Failed to process message with ID %s.", messageId);

            return envelope != null
                ? baseMessage + String.format(" Envelope ID: %s, File name: %s", envelope.id, envelope.zipFileName)
                : baseMessage;
        }
    }
}
