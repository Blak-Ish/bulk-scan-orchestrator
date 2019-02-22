package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.MessageProcessingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType;

@Component
public class EnvelopeProcessorFinaliser {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeProcessorFinaliser.class);

    private final IMessageOperations messageOperations;

    public EnvelopeProcessorFinaliser(IMessageOperations messageOperations) {
        this.messageOperations = messageOperations;
    }

    public void tryFinaliseProcessedMessage(
        IMessage message,
        MessageProcessingResultType resultType,
        Exception resultException
    ) {
        try {
            finaliseProcessedMessage(message, resultType, resultException);
        } catch (InterruptedException exception) {
            logMessageFinaliseError(message, resultType, exception);
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            logMessageFinaliseError(message, resultType, exception);
        }
    }

    private void finaliseProcessedMessage(
        IMessage message,
        MessageProcessingResultType resultType,
        Exception resultException
    ) throws InterruptedException, ServiceBusException {
        switch (resultType) {
            case SUCCESS:
                messageOperations.complete(message.getLockToken());

                log.info("Message with ID {} has been completed", message.getMessageId());

                break;
            case UNRECOVERABLE_FAILURE:
                messageOperations.deadLetter(
                    message.getLockToken(),
                    "Message processing error",
                    resultException.getMessage()
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
                    "Unknown message processing result type: " + resultType
                );
        }
    }

    private void logMessageFinaliseError(
        IMessage message,
        MessageProcessingResultType processingResultType,
        Exception exception
    ) {
        log.error(
            "Failed to manage processed message with ID {}. Processing result: {}",
            message.getMessageId(),
            processingResultType,
            exception
        );
    }
}
