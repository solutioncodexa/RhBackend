package ma.rhflow.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;
import ma.rhflow.domain.entity.Contract;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.repository.ContractRepository;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.tenant.TenantContext;

@Service
@RequiredArgsConstructor
public class PdfDocumentService {

    private final TenantRepository tenantRepository;
    private final ContractRepository contractRepository;

    public byte[] attestation(Employee employee, String type) {
        return attestation(employee, type, null);
    }

    public byte[] attestation(Employee employee, String type, String extra) {
        Tenant company = tenantRepository.findById(TenantContext.requireTenantId()).orElse(null);
        Contract contract = contractRepository.findByEmployeeIdAndStatus(employee.getId(), "ACTIVE").stream()
                .findFirst().orElse(null);
        String kind = type == null ? "ATTESTATION_TRAVAIL" : type.toUpperCase();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font h = new Font(Font.HELVETICA, 14, Font.BOLD, Color.BLACK);
            Font n = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY);
            String title = titleFor(kind);
            doc.add(new Paragraph(title, h));
            doc.add(new Paragraph(" "));
            if (company != null) {
                doc.add(new Paragraph(nz(company.getLegalName(), company.getName()), n));
                doc.add(new Paragraph("ICE : " + nz(company.getIce(), "—") + " · IF : " + nz(company.getTaxId(), "—")
                        + " · RC : " + nz(company.getRc(), "—"), n));
                doc.add(new Paragraph(nz(company.getAddress(), "") + " " + nz(company.getCity(), ""), n));
            }
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Nous soussignés certifions que " + employee.getFirstName() + " " + employee.getLastName()
                    + " (CIN " + nz(employee.getCin(), "—") + ") est salarié(e) de la société.", n));
            if (employee.getHireDate() != null) {
                doc.add(new Paragraph("Date d’entrée : " + employee.getHireDate(), n));
            }
            if (contract != null) {
                doc.add(new Paragraph("Type de contrat : " + contract.getType(), n));
                if (kind.contains("SALAIRE") || kind.contains("SALARY")) {
                    BigDecimal salary = contract.getBaseSalary() == null ? BigDecimal.ZERO : contract.getBaseSalary();
                    doc.add(new Paragraph("Salaire de base : " + salary.toPlainString() + " MAD", n));
                }
            }
            if (extra != null && !extra.isBlank()) {
                doc.add(new Paragraph(extra, n));
            }
            doc.add(new Paragraph("Fait à " + (company == null ? "Casablanca" : nz(company.getCity(), "Casablanca"))
                    + ", le " + LocalDate.now(), n));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String titleFor(String kind) {
        if (kind.contains("SALAIRE") || kind.contains("SALARY")) {
            return "Attestation de salaire";
        }
        if (kind.contains("EMPLOI") || kind.contains("EMPLOYMENT")) {
            return "Attestation d’emploi";
        }
        if (kind.contains("CONGE") || kind.contains("LEAVE")) {
            return "Autorisation de congé";
        }
        if (kind.contains("CONTRAT") || kind.contains("CONTRACT")) {
            return "Contrat de travail";
        }
        return "Attestation de travail";
    }

    private String nz(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
