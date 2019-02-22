package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler;

import com.google.common.base.Strings;
import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisherContainer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeProcessorFinaliser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.NotificationSendingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
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
    private final EnvelopeProcessorFinaliser finaliser;

    public EnvelopeEventProcessor(
        CaseRetriever caseRetriever,
        EventPublisherContainer eventPublisherContainer,
        IProcessedEnvelopeNotifier processedEnvelopeNotifier,
        EnvelopeProcessorFinaliser finaliser
    ) {
        this.caseRetriever = caseRetriever;
        this.eventPublisherContainer = eventPublisherContainer;
        this.processedEnvelopeNotifier = processedEnvelopeNotifier;
        this.finaliser = finaliser;
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

            finaliser.tryFinaliseProcessedMessage(message, result.resultType, result.exception);

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
