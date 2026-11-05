package com.coolapp.medibook.patient;

import com.coolapp.medibook.patient.PatientDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patients;
    private final DocumentService documents;
    private final DataExportService export;

    @GetMapping("/patients/me")
    public PatientDto me() {
        return patients.me();
    }

    @PutMapping("/patients/me")
    public PatientDto updateMe(@Valid @RequestBody PatientUpdateRequest req) {
        return patients.updateMe(req);
    }

    @GetMapping("/patients/me/dependents")
    public List<PatientDto> dependents() {
        return patients.dependents();
    }

    @PostMapping("/patients/me/dependents")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientDto addDependent(@Valid @RequestBody PatientUpdateRequest req) {
        return patients.addDependent(req);
    }

    @PutMapping("/patients/me/dependents/{id}")
    public PatientDto updateDependent(@PathVariable UUID id, @Valid @RequestBody PatientUpdateRequest req) {
        return patients.updateDependent(id, req);
    }

    @DeleteMapping("/patients/me/dependents/{id}")
    public ResponseEntity<Void> removeDependent(@PathVariable UUID id) {
        patients.removeDependent(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/patients/me/access-log")
    public List<AccessLogDto> accessLog(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return patients.myAccessLog(page, Math.min(size, 200));
    }

    @GetMapping("/patients/me/export")
    public ResponseEntity<Map<String, Object>> exportData() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"medibook-my-data.json\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(export.exportMine());
    }

    @GetMapping("/patients/{id}/medical-history")
    public List<MedicalHistoryDto> history(@PathVariable UUID id) {
        return patients.history(id);
    }

    @PostMapping("/patients/{id}/medical-history")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicalHistoryDto addHistory(@PathVariable UUID id, @Valid @RequestBody MedicalHistoryRequest req) {
        return patients.addHistory(id, req);
    }

    @PutMapping("/patients/{id}/medical-history/{historyId}")
    public MedicalHistoryDto updateHistory(@PathVariable UUID id, @PathVariable UUID historyId,
                                           @Valid @RequestBody MedicalHistoryRequest req) {
        return patients.updateHistory(id, historyId, req);
    }

    @DeleteMapping("/patients/{id}/medical-history/{historyId}")
    public ResponseEntity<Void> deleteHistory(@PathVariable UUID id, @PathVariable UUID historyId) {
        patients.deleteHistory(id, historyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/patients/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                              @RequestParam(name = "docType", required = false) DocumentType docType) {
        return documents.upload(id, file, docType);
    }

    @GetMapping("/patients/{id}/documents")
    public List<DocumentDto> documents(@PathVariable UUID id) {
        return documents.list(id);
    }

    /** Returns a short-lived signed URL; the bucket itself is private. */
    @GetMapping("/documents/{id}/download")
    public SignedUrlDto download(@PathVariable UUID id) {
        return documents.downloadUrl(id);
    }
}
