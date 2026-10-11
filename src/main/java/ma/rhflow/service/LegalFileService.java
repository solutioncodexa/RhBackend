package ma.rhflow.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.Payslip;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.PayslipRepository;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.tenant.TenantContext;

@Service
@RequiredArgsConstructor
public class LegalFileService {

    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;
    private final TenantRepository tenantRepository;

    public ResponseEntity<byte[]> damancomEdi(int year, int month) {
        Tenant t = tenant();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<BDS xmlns=\"urn:cnss:damancom:bds:v1\">\n");
        xml.append("  <Employeur affiliation=\"").append(esc(t.getCnssAffiliation()))
                .append("\" ice=\"").append(esc(t.getIce()))
                .append("\" raison=\"").append(esc(nz(t.getLegalName(), t.getName()))).append("\"/>\n");
        xml.append("  <Periode annee=\"").append(year).append("\" mois=\"").append(pad(month)).append("\"/>\n");
        xml.append("  <Salaries>\n");
        for (Payslip p : payslipRepository.findAll()) {
            if (!match(p, year, month)) {
                continue;
            }
            Employee e = employeeRepository.findById(p.getEmployeeId()).orElse(null);
            if (e == null) {
                continue;
            }
            xml.append("    <Salarie cnss=\"").append(esc(first(p.getSnapshotCnss(), e.getCnssNumber())))
                    .append("\" cin=\"").append(esc(e.getCin()))
                    .append("\" nom=\"").append(esc(e.getLastName()))
                    .append("\" prenom=\"").append(esc(e.getFirstName()))
                    .append("\" mouvement=\"").append(mouvement(e, year, month))
                    .append("\" dateEntree=\"").append(e.getHireDate() == null ? "" : e.getHireDate())
                    .append("\" dateSortie=\"").append(e.getExitDate() == null ? "" : e.getExitDate())
                    .append("\" jours=\"").append(p.getDeclaredDays() == null ? 26 : p.getDeclaredDays())
                    .append("\" salaire=\"").append(n(p.getGross()))
                    .append("\"/>\n");
        }
        xml.append("  </Salaries>\n</BDS>\n");
        return file(xml.toString().getBytes(StandardCharsets.UTF_8),
                "damancom-" + year + "-" + pad(month) + ".xml", MediaType.APPLICATION_XML_VALUE);
    }

    public ResponseEntity<byte[]> etat9421Edi(int year) {
        Tenant t = tenant();
        Map<Long, Payslip> annual = new LinkedHashMap<>();
        for (Payslip p : payslipRepository.findByYear(year)) {
            Payslip acc = annual.computeIfAbsent(p.getEmployeeId(), id -> emptySlip(id));
            acc.setGross(n(acc.getGross()).add(n(p.getGross())));
            acc.setCnss(n(acc.getCnss()).add(n(p.getCnss())));
            acc.setAmo(n(acc.getAmo()).add(n(p.getAmo())));
            acc.setIr(n(acc.getIr()).add(n(p.getIr())));
            acc.setNet(n(acc.getNet()).add(n(p.getNet())));
        }
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Etat9421 xmlns=\"urn:dgi:etat9421:v1\" annee=\"").append(year).append("\">\n");
        xml.append("  <Employeur ice=\"").append(esc(t.getIce())).append("\" if=\"").append(esc(t.getTaxId()))
                .append("\" raison=\"").append(esc(nz(t.getLegalName(), t.getName()))).append("\"/>\n");
        for (Payslip p : annual.values()) {
            Employee e = employeeRepository.findById(p.getEmployeeId()).orElse(null);
            if (e == null) {
                continue;
            }
            xml.append("  <Salarie cin=\"").append(esc(e.getCin()))
                    .append("\" cnss=\"").append(esc(e.getCnssNumber()))
                    .append("\" nom=\"").append(esc(e.getLastName()))
                    .append("\" prenom=\"").append(esc(e.getFirstName()))
                    .append("\" brut=\"").append(n(p.getGross()))
                    .append("\" cnssAmo=\"").append(n(p.getCnss()).add(n(p.getAmo())))
                    .append("\" ir=\"").append(n(p.getIr()))
                    .append("\" net=\"").append(n(p.getNet()))
                    .append("\"/>\n");
        }
        xml.append("</Etat9421>\n");
        return file(xml.toString().getBytes(StandardCharsets.UTF_8), "etat-9421-" + year + ".xml", MediaType.APPLICATION_XML_VALUE);
    }

    public ResponseEntity<byte[]> virementSimt(int year, int month) {
        Tenant t = tenant();
        String msgId = "RHF" + year + pad(month) + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal total = BigDecimal.ZERO;
        StringBuilder txs = new StringBuilder();
        int ntx = 0;
        for (Payslip p : payslipRepository.findAll()) {
            if (!match(p, year, month)) {
                continue;
            }
            Employee e = employeeRepository.findById(p.getEmployeeId()).orElse(null);
            if (e == null) {
                continue;
            }
            String rib = first(p.getSnapshotRib(), e.getRib());
            if (rib == null || rib.isBlank()) {
                continue;
            }
            total = total.add(n(p.getNet()));
            ntx++;
            String endToEnd = "VIR-" + year + "-" + pad(month) + "-" + e.getId();
            txs.append("      <CdtTrfTxInf>\n");
            txs.append("        <PmtId><EndToEndId>").append(esc(endToEnd)).append("</EndToEndId></PmtId>\n");
            txs.append("        <Amt><InstdAmt Ccy=\"MAD\">").append(n(p.getNet())).append("</InstdAmt></Amt>\n");
            txs.append("        <Cdtr><Nm>").append(esc(e.getLastName() + " " + e.getFirstName())).append("</Nm></Cdtr>\n");
            txs.append("        <CdtrAcct><Id><Othr><Id>").append(esc(rib.replaceAll("\\s", "")))
                    .append("</Id></Othr></Id></CdtrAcct>\n");
            txs.append("        <RmtInf><Ustrd>Salaire ").append(pad(month)).append("/").append(year).append("</Ustrd></RmtInf>\n");
            txs.append("      </CdtTrfTxInf>\n");
        }
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.03\">\n");
        xml.append("  <CstmrCdtTrfInitn>\n    <GrpHdr>\n");
        xml.append("      <MsgId>").append(msgId).append("</MsgId>\n");
        xml.append("      <CreDtTm>").append(LocalDate.now()).append("T08:00:00</CreDtTm>\n");
        xml.append("      <NbOfTxs>").append(ntx).append("</NbOfTxs>\n");
        xml.append("      <CtrlSum>").append(total).append("</CtrlSum>\n");
        xml.append("      <InitgPty><Nm>").append(esc(nz(t.getLegalName(), t.getName()))).append("</Nm></InitgPty>\n");
        xml.append("    </GrpHdr>\n    <PmtInf>\n");
        xml.append("      <PmtInfId>").append(msgId).append("-P</PmtInfId>\n");
        xml.append("      <PmtMtd>TRF</PmtMtd>\n");
        xml.append("      <ReqdExctnDt>").append(LocalDate.now()).append("</ReqdExctnDt>\n");
        xml.append("      <Dbtr><Nm>").append(esc(nz(t.getLegalName(), t.getName()))).append("</Nm></Dbtr>\n");
        xml.append(txs);
        xml.append("    </PmtInf>\n  </CstmrCdtTrfInitn>\n</Document>\n");
        return file(xml.toString().getBytes(StandardCharsets.UTF_8),
                "virement-simt-" + year + "-" + pad(month) + ".xml", MediaType.APPLICATION_XML_VALUE);
    }

    public ResponseEntity<byte[]> cimr(int year, int month) {
        StringBuilder sb = new StringBuilder("cnss,nom,prenom,cin,base,part_salarie,part_employeur,periode\n");
        for (Payslip p : payslipRepository.findAll()) {
            if (!match(p, year, month)) {
                continue;
            }
            Employee e = employeeRepository.findById(p.getEmployeeId()).orElse(null);
            if (e == null) {
                continue;
            }
            sb.append(csv(e.getCnssNumber())).append(',').append(csv(e.getLastName())).append(',')
                    .append(csv(e.getFirstName())).append(',').append(csv(e.getCin())).append(',')
                    .append(n(p.getGross())).append(',').append(n(p.getCimrEmployee())).append(',')
                    .append(n(p.getCimrEmployer())).append(',').append(pad(month)).append('/').append(year).append('\n');
        }
        return file(sb.toString().getBytes(StandardCharsets.UTF_8), "cimr-" + year + "-" + pad(month) + ".csv", "text/csv");
    }

    private Tenant tenant() {
        Long id = TenantContext.requireTenantId();
        return tenantRepository.findById(id).orElseGet(Tenant::new);
    }

    private static boolean match(Payslip p, int year, int month) {
        return p.getYear() != null && p.getYear() == year && (month == 0 || (p.getMonth() != null && p.getMonth() == month));
    }

    private static Payslip emptySlip(Long id) {
        Payslip x = new Payslip();
        x.setEmployeeId(id);
        x.setGross(BigDecimal.ZERO);
        x.setCnss(BigDecimal.ZERO);
        x.setAmo(BigDecimal.ZERO);
        x.setIr(BigDecimal.ZERO);
        x.setNet(BigDecimal.ZERO);
        return x;
    }

    private static String mouvement(Employee e, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        if (e.getHireDate() != null && !e.getHireDate().isBefore(start) && !e.getHireDate().isAfter(end)) {
            return "E";
        }
        if (e.getExitDate() != null && !e.getExitDate().isBefore(start) && !e.getExitDate().isAfter(end)) {
            return "S";
        }
        return "M";
    }

    private static ResponseEntity<byte[]> file(byte[] bytes, String name, String type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType(type))
                .body(bytes);
    }

    private static String esc(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String first(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    private static String nz(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String pad(int month) {
        return month < 10 ? "0" + month : String.valueOf(month);
    }
}
