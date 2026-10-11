package ma.rhflow.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.annotations.Filter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.tenant.TenantScoped;

@Entity
@Table(name = "employees", uniqueConstraints = {
        @UniqueConstraint(name = "uq_employee_tenant_email", columnNames = {"tenant_id", "email"})
})
@Getter
@Setter
@Filter(name = TenantScoped.FILTER_NAME, condition = "tenant_id = :" + TenantScoped.PARAM_NAME)
public class Employee extends TenantScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;
    private String email;
    private String cin;
    private String phone;
    private String city;
    private LocalDate hireDate;
    private LocalDate exitDate;
    private String cnssNumber;
    private LocalDate cnssRegisteredOn;
    private String amoNumber;
    private String insurerName;
    private String insurerPolicy;
    private LocalDate insurerStart;
    private String bloodType;
    @Column(length = 2000)
    private String medicalNotes;
    private String rib;
    private Long departmentId;
    private Long positionId;
    private Long managerId;
    private String status = "ACTIVE";
    private Integer dependents = 0;
    private LocalDate birthDate;
    private String gender;
    private String nationality;
    private String maritalStatus;
    private Integer childrenCount = 0;
    private String address;
    private String personalEmail;
    private String emergencyName;
    private String emergencyRelation;
    private String emergencyPhone;
    private String photoPath;
    private Long establishmentId;
    private String bankName;
    private String bankBic;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
