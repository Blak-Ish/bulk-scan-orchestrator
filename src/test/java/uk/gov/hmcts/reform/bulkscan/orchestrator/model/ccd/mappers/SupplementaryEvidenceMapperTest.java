package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class SupplementaryEvidenceMapperTest {

    private static final SupplementaryEvidenceMapper mapper = new SupplementaryEvidenceMapper();

    @Test
    public void maps_all_fields_correctly() {
        // given
        Envelope envelope = SampleData.envelope(1);

        // when
        SupplementaryEvidence supplementaryEvidence = mapper.map(envelope);

        // then
        assertThat(supplementaryEvidence.evidenceHandled).isEqualTo("No");

        assertThat(supplementaryEvidence.scannedDocuments.size()).isEqualTo(1);

        Document envelopeDocument = envelope.documents.get(0);
        ScannedDocument scannedDocument = supplementaryEvidence.scannedDocuments.get(0).value;

        assertThat(scannedDocument.controlNumber).isEqualTo(envelopeDocument.controlNumber);
        assertThat(scannedDocument.fileName).isEqualTo(envelopeDocument.fileName);
        assertThat(scannedDocument.type).isEqualTo(envelopeDocument.type);
        assertThat(scannedDocument.url.documentUrl).isEqualTo(envelopeDocument.url);

        LocalDateTime expectedScannedDate =
            envelopeDocument.scannedAt.atZone(ZoneId.systemDefault()).toLocalDateTime();

        assertThat(scannedDocument.scannedDate).isEqualTo(expectedScannedDate);
    }

    @Test
    public void returns_supplementary_evidence_with_all_documents() {
        // given
        int numberOfDocuments = 12;
        Envelope envelope = SampleData.envelope(12);

        // when
        SupplementaryEvidence supplementaryEvidence = mapper.map(envelope);

        // then
        assertThat(supplementaryEvidence.scannedDocuments.size()).isEqualTo(numberOfDocuments);

        List<String> expectedDocumentFileNames =
            envelope.documents.stream().map(d -> d.fileName).collect(toList());

        List<String> actualDocumentFileNames =
            supplementaryEvidence.scannedDocuments.stream().map(d -> d.value.fileName).collect(toList());

        assertThat(actualDocumentFileNames).isEqualTo(expectedDocumentFileNames);
    }

    @Test
    public void returns_supplementary_evidence_with_subtype_value_in_documents() {
        // given
        int numberOfDocuments = 3;
        Envelope envelope = SampleData.envelope(3);

        // when
        SupplementaryEvidence supplementaryEvidence = mapper.map(envelope);

        // then
        assertThat(supplementaryEvidence.scannedDocuments.size()).isEqualTo(numberOfDocuments);

        List<String> expectedDocumentSubtypeValues =
            envelope.documents.stream().map(d -> d.subtype).collect(toList());

        List<String> actualDocumentSubtypeValues =
            supplementaryEvidence.scannedDocuments.stream().map(d -> d.value.subtype).collect(toList());

        assertThat(actualDocumentSubtypeValues).isEqualTo(expectedDocumentSubtypeValues);
    }

    @Test
    public void handles_empty_document_list() {
        Envelope envelope = SampleData.envelope(0);
        SupplementaryEvidence supplementaryEvidence = mapper.map(envelope);

        assertThat(supplementaryEvidence.scannedDocuments).isEmpty();
    }
}
