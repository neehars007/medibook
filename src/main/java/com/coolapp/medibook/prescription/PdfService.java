package com.coolapp.medibook.prescription;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

/** Generates visit-summary, prescription and receipt PDFs with OpenPDF. */
@Service
public class PdfService {

    public record Header(String clinicName, String clinicAddress, String clinicPhone) {}

    public record RxLine(String medicine, String dosage, String frequency, Integer durationDays, String instructions) {}

    public record VisitSummary(Header header, String patientName, String patientDob, String doctorName, String doctorLicense,
                               String visitDate, String chiefComplaint, String findings, String vitals, String diagnosis,
                               String icdCode, String advice, String recommendedTests, Integer followUpInDays,
                               List<String> addendums, List<RxLine> prescription) {}

    public record PrescriptionDoc(Header header, String patientName, String patientDob, String doctorName,
                                  String doctorLicense, String date, List<RxLine> items) {}

    public record Receipt(Header header, String receiptNo, String patientName, String doctorName, String visitDate,
                          String method, BigDecimal amount, BigDecimal refunded, String paidAt) {}

    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8);

    public byte[] visitSummary(VisitSummary v) {
        return render(doc -> {
            header(doc, v.header(), "Visit Summary");
            kv(doc, "Patient", v.patientName() + "  (DOB " + v.patientDob() + ")");
            kv(doc, "Doctor", v.doctorName() + "  - Reg. " + v.doctorLicense());
            kv(doc, "Date", v.visitDate());
            section(doc, "Chief complaint", v.chiefComplaint());
            section(doc, "Examination findings", v.findings());
            section(doc, "Vitals", v.vitals());
            section(doc, "Diagnosis", v.diagnosis() + (v.icdCode() == null ? "" : "  [ICD-10 " + v.icdCode() + "]"));
            if (v.prescription() != null && !v.prescription().isEmpty()) {
                doc.add(new Paragraph("Prescription", H2));
                doc.add(rxTable(v.prescription()));
            }
            section(doc, "Advice", v.advice());
            section(doc, "Recommended tests", v.recommendedTests());
            if (v.followUpInDays() != null) {
                section(doc, "Follow-up", "In " + v.followUpInDays() + " days");
            }
            if (v.addendums() != null && !v.addendums().isEmpty()) {
                doc.add(new Paragraph("Addendums", H2));
                for (String a : v.addendums()) {
                    doc.add(new Paragraph(a, BODY));
                }
            }
            footer(doc);
        });
    }

    public byte[] prescription(PrescriptionDoc p) {
        return render(doc -> {
            header(doc, p.header(), "Prescription");
            kv(doc, "Patient", p.patientName() + "  (DOB " + p.patientDob() + ")");
            kv(doc, "Prescriber", p.doctorName() + "  - Reg. " + p.doctorLicense());
            kv(doc, "Date", p.date());
            doc.add(new Paragraph(" "));
            doc.add(rxTable(p.items()));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Signed electronically by " + p.doctorName(), BODY));
            footer(doc);
        });
    }

    public byte[] receipt(Receipt r) {
        return render(doc -> {
            header(doc, r.header(), "Payment Receipt");
            kv(doc, "Receipt #", r.receiptNo());
            kv(doc, "Patient", r.patientName());
            kv(doc, "Consultation", r.doctorName() + " on " + r.visitDate());
            kv(doc, "Method", r.method());
            kv(doc, "Amount paid", "$" + r.amount().setScale(2, java.math.RoundingMode.HALF_UP));
            if (r.refunded() != null && r.refunded().signum() > 0) {
                kv(doc, "Refunded", "$" + r.refunded().setScale(2, java.math.RoundingMode.HALF_UP));
            }
            kv(doc, "Paid at", r.paidAt());
            footer(doc);
        });
    }

    private PdfPTable rxTable(List<RxLine> items) {
        PdfPTable t = new PdfPTable(new float[]{3, 2, 2, 1.2f, 3});
        t.setWidthPercentage(100);
        for (String h : List.of("Medicine", "Dosage", "Frequency", "Days", "Instructions")) {
            PdfPCell c = new PdfPCell(new Phrase(h, H2));
            c.setPadding(5);
            t.addCell(c);
        }
        for (RxLine i : items) {
            t.addCell(cell(i.medicine()));
            t.addCell(cell(i.dosage()));
            t.addCell(cell(i.frequency()));
            t.addCell(cell(i.durationDays() == null ? "" : i.durationDays().toString()));
            t.addCell(cell(i.instructions()));
        }
        return t;
    }

    private static PdfPCell cell(String s) {
        PdfPCell c = new PdfPCell(new Phrase(s == null ? "" : s, BODY));
        c.setPadding(5);
        return c;
    }

    private static void header(Document doc, Header h, String title) throws Exception {
        doc.add(new Paragraph(h.clinicName(), H1));
        doc.add(new Paragraph(nz(h.clinicAddress()) + "   " + nz(h.clinicPhone()), SMALL));
        Paragraph p = new Paragraph(title, H2);
        p.setSpacingBefore(14);
        p.setSpacingAfter(10);
        p.setAlignment(Element.ALIGN_LEFT);
        doc.add(p);
    }

    private static void kv(Document doc, String k, String v) throws Exception {
        Paragraph p = new Paragraph();
        p.add(new Phrase(k + ": ", H2));
        p.add(new Phrase(nz(v), BODY));
        doc.add(p);
    }

    private static void section(Document doc, String title, String body) throws Exception {
        if (body == null || body.isBlank()) {
            return;
        }
        Paragraph t = new Paragraph(title, H2);
        t.setSpacingBefore(8);
        doc.add(t);
        doc.add(new Paragraph(body, BODY));
    }

    private static void footer(Document doc) throws Exception {
        Paragraph p = new Paragraph("Generated by MediBook. Portfolio demo - not a real medical document.", SMALL);
        p.setSpacingBefore(24);
        doc.add(p);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @FunctionalInterface
    private interface PdfBody {
        void write(Document doc) throws Exception;
    }

    private byte[] render(PdfBody body) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter.getInstance(doc, out);
            doc.open();
            body.write(doc);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF generation failed", e);
        }
    }
}
