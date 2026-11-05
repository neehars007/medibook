package com.coolapp.medibook.patient;

import com.coolapp.medibook.audit.AccessResource;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.common.config.AppProperties;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.common.security.CurrentUser;
import com.coolapp.medibook.patient.PatientDtos.DocumentDto;
import com.coolapp.medibook.patient.PatientDtos.SignedUrlDto;
import com.coolapp.medibook.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Map<String, byte[]> MAGIC = Map.of(
            "application/pdf", new byte[]{'%', 'P', 'D', 'F'},
            "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'},
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    private final PatientDocumentRepository documents;
    private final PatientAccessService access;
    private final StorageService storage;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    @Transactional
    public DocumentDto upload(UUID patientId, MultipartFile file, DocumentType type) {
        access.requireHistoryEdit(patientId); // patient/guardian or treating doctor
        validate(file);
        String safeName = file.getOriginalFilename() == null ? "upload"
                : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_");
        String key = "patients/" + patientId + "/documents/" + UUID.randomUUID() + "-" + safeName;
        try {
            storage.put(key, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read upload");
        }
        PatientDocument d = new PatientDocument();
        d.setPatientId(patientId);
        d.setFileKey(key);
        d.setFileName(safeName);
        d.setMimeType(file.getContentType());
        d.setSizeBytes(file.getSize());
        d.setDocType(type == null ? DocumentType.OTHER : type);
        d.setUploadedBy(CurrentUser.get().id());
        d.setUploadedAt(clock.instant());
        documents.save(d);
        audit.log("DOCUMENT_UPLOADED", "PatientDocument", d.getId(), d.getDocType().name());
        return DocumentDto.from(d);
    }

    @Transactional
    public List<DocumentDto> list(UUID patientId) {
        access.requireClinicalAccess(patientId, AccessResource.DOCUMENT, null);
        return documents.findByPatientIdOrderByUploadedAtDesc(patientId).stream().map(DocumentDto::from).toList();
    }

    @Transactional
    public SignedUrlDto downloadUrl(UUID documentId) {
        PatientDocument d = documents.findById(documentId).orElseThrow(() -> ApiException.notFound("Document"));
        access.requireClinicalAccess(d.getPatientId(), AccessResource.DOCUMENT, documentId);
        return new SignedUrlDto(storage.signedDownloadUrl(d.getFileKey()), clock.instant().plus(props.storage().signedUrlTtl()));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is empty");
        }
        if (file.getSize() > props.storage().maxUploadBytes()) {
            throw ApiException.badRequest("File too large (max " + props.storage().maxUploadBytes() / (1024 * 1024) + " MB)");
        }
        byte[] magic = MAGIC.get(file.getContentType());
        if (magic == null) {
            throw ApiException.badRequest("Only PDF, PNG and JPEG files are allowed");
        }
        try {
            byte[] head = Arrays.copyOf(file.getBytes(), magic.length);
            if (!Arrays.equals(head, magic)) {
                throw ApiException.badRequest("File content does not match its type");
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read upload");
        }
    }
}
