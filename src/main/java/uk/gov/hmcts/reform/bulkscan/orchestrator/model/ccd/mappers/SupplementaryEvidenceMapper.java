package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import com.google.common.collect.Lists;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;
import java.util.Objects;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.DocumentMapper.mapDocuments;

@Component
public class SupplementaryEvidenceMapper {

    public SupplementaryEvidenceMapper() {
        // empty mapper construct
    }

    public SupplementaryEvidence map(Envelope envelope, StartEventResponse startEventResponse) {

        List<Document> existingDocuments = ScannedDocumentsHelper.getDocuments(startEventResponse.getCaseDetails());
        List<Document> newDocuments = envelope.documents;

        List<Document> targetDocuments = Lists.newArrayList(existingDocuments);

        newDocuments.forEach(d -> {
            if (targetDocuments.stream().noneMatch(existing -> Objects.equals(existing.url, d.url))) {
                targetDocuments.add(d);
            }
        });

        return new SupplementaryEvidence(mapDocuments(targetDocuments));
    }
}
