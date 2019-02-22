package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.SUCCESS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.UNRECOVERABLE_FAILURE;

public class MessageProcessingResult {

    final MessageProcessingResultType resultType;

    final Envelope envelope;

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

    boolean isSuccess() {
        return MessageProcessingResultType.SUCCESS.equals(resultType);
    }

    MessageProcessingResult andThen(Supplier<MessageProcessingResult> function) {
        return isSuccess() ? function.get() : this;
    }

    <T> MessageProcessingResult andThenWithEnvelope(
        BiFunction<T, Envelope, MessageProcessingResult> function,
        T argument
    ) {
        return andThen(() -> function.apply(argument, envelope));
    }
}
