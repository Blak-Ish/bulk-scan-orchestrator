package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler;

import com.google.common.base.Strings;
import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisherContainer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IMessageOperations;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.NotificationSendingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.MessageProcessingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser.parse;

@Service
public class EnvelopeEventProcessor implements IMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeEventProcessor.class);

    private final CaseRetriever caseRetriever;
    private final EventPublisherContainer eventPublisherContainer;
    private final IProcessedEnvelopeNotifier processedEnvelopeNotifier;
    private final IMessageOperations messageOperations;

    public EnvelopeEventProcessor(
        CaseRetriever caseRetriever,
        EventPublisherContainer eventPublisherContainer,
        IProcessedEnvelopeNotifier processedEnvelopeNotifier,
        IMessageOperations messageOperations
    ) {
        this.caseRetriever = caseRetriever;
        this.eventPublisherContainer = eventPublisherContainer;
        this.processedEnvelopeNotifier = processedEnvelopeNotifier;
        this.messageOperations = messageOperations;
    }

    @Override
    public CompletableFuture<Void> onMessageAsync(IMessage message) {
        /*
         * NOTE: this is done here instead of offloading to the forkJoin pool "CompletableFuture.runAsync()"
         * because we probably should think about a threading model before doing this.
         * Maybe consider using Netflix's RxJava too (much simpler than CompletableFuture).
         */
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        try {
            MessageProcessingResult result = parseEnvelope(message)
                .andThenWithEnvelope(this::publishEnvelope)
                .andThenWithEnvelope(this::notifyProcessedEnvelope)
                .logProcessFinish(message.getMessageId());

            tryFinaliseProcessedMessage(message, result);

            completableFuture.complete(null);
        } catch (Throwable t) {
            completableFuture.completeExceptionally(t);
        }
        return completableFuture;
    }

    private MessageProcessingResult parseEnvelope(IMessage message) {
        String messageId = message.getMessageId();

        log.info("Started processing message with ID {}", messageId);

        try {
            return MessageProcessingResult.success(parse(messageId, message.getBody()));
        } catch (InvalidMessageException exception) {
            return MessageProcessingResult.unrecoverable(exception);
        } catch (Exception exception) {
            return MessageProcessingResult.recoverable(exception);
        }
    }

    private MessageProcessingResult publishEnvelope(Envelope envelope) {
        try {
            EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
                envelope.classification,
                getCaseRetriever(envelope)
            );

            eventPublisher.publish(envelope);

            return MessageProcessingResult.success(envelope);
        } catch (Exception ex) {
            return MessageProcessingResult.recoverable(envelope, ex);
        }
    }

    private MessageProcessingResult notifyProcessedEnvelope(Envelope envelope) {
        try {
            processedEnvelopeNotifier.notify(envelope.id);

            return MessageProcessingResult.success(envelope);
        } catch (NotificationSendingException ex) {
            // CCD changes have been made, so it's better to dead-letter the message and
            // not repeat them, at least until CCD operations become idempotent
            return MessageProcessingResult.unrecoverable(envelope, ex);
        }
    }

    private void tryFinaliseProcessedMessage(IMessage message, MessageProcessingResult processingResult) {
        try {
            finaliseProcessedMessage(message, processingResult);
        } catch (InterruptedException ex) {
            logMessageFinaliseError(message, processingResult.resultType, ex);
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            logMessageFinaliseError(message, processingResult.resultType, ex);
        }
    }

    private void finaliseProcessedMessage(
        IMessage message,
        MessageProcessingResult processingResult
    ) throws InterruptedException, ServiceBusException {

        switch (processingResult.resultType) {
            case SUCCESS:
                messageOperations.complete(message.getLockToken());
                log.info("Message with ID {} has been completed", message.getMessageId());
                break;
            case UNRECOVERABLE_FAILURE:
                messageOperations.deadLetter(
                    message.getLockToken(),
                    "Message processing error",
                    processingResult.exception.getMessage()
                );

                log.info("Message with ID {} has been dead-lettered", message.getMessageId());
                break;
            case POTENTIALLY_RECOVERABLE_FAILURE:
                // do nothing - let the message lock expire
                log.info(
                    "Allowing message with ID {} to return to queue (delivery attempt {})",
                    message.getMessageId(),
                    message.getDeliveryCount() + 1
                );
                break;
            default:
                throw new MessageProcessingException(
                    "Unknown message processing result type: " + processingResult.resultType
                );
        }
    }

    private void logMessageFinaliseError(
        IMessage message,
        MessageProcessingResultType processingResultType,
        Exception ex
    ) {
        log.error(
            "Failed to manage processed message with ID {}. Processing result: {}",
            message.getMessageId(),
            processingResultType,
            ex
        );
    }

    @Override
    public void notifyException(Throwable exception, ExceptionPhase phase) {
        log.error("Error while handling message at stage: " + phase, exception);
    }

    private Supplier<CaseDetails> getCaseRetriever(final Envelope envelope) {
        return () -> Strings.isNullOrEmpty(envelope.caseRef)
            ? null
            : caseRetriever.retrieve(envelope.jurisdiction, envelope.caseRef);
    }
}
