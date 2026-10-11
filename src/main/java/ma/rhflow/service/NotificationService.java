package ma.rhflow.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.Notification;
import ma.rhflow.domain.entity.User;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.NotificationRepository;
import ma.rhflow.domain.repository.UserRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.tenant.TenantContext;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final MailService mailService;
    private final WhatsAppService whatsAppService;

    @Transactional
    public void notifyUser(Long userId, String title, String message) {
        notifyUser(userId, title, message, null);
    }

    @Transactional
    public void notifyUser(Long userId, String title, String message, String link) {
        if (userId == null) {
            return;
        }
        Notification n = new Notification();
        n.setTenantId(TenantContext.getTenantId());
        n.setUserId(userId);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        n.setReadFlag(false);
        notificationRepository.save(n);
    }

    @Transactional
    public void emailEmployee(Long employeeId, String subject, String text, String path, String button) {
        if (employeeId == null) {
            return;
        }
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return;
        }
        mailService.send(employee.getEmail(), subject, text, path, button);
        whatsAppService.notify(employee.getPhone(), subject + "\n" + text);
    }

    @Transactional
    public void emailAdmins(String subject, String text, String path, String button) {
        Long tenantId = TenantContext.getTenantId();
        for (User user : userRepository.findByTenantIdOrderByLastNameAscFirstNameAsc(tenantId)) {
            if (user.getRole() == UserRole.ADMIN) {
                mailService.send(user.getEmail(), subject, text, path, button);
            }
        }
    }

    public void emailManagerOf(Long employeeId, String subject, String text, String path, String button) {
        if (employeeId == null) {
            emailAdmins(subject, text, path, button);
            return;
        }
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null || employee.getManagerId() == null) {
            emailAdmins(subject, text, path, button);
            return;
        }
        emailEmployee(employee.getManagerId(), subject, text, path, button);
    }

    @Transactional
    public void notifyEmployee(Long employeeId, String title, String message) {
        notifyEmployee(employeeId, title, message, null);
    }

    @Transactional
    public void notifyEmployee(Long employeeId, String title, String message, String link) {
        if (employeeId == null) {
            return;
        }
        userRepository.findByTenantIdOrderByLastNameAscFirstNameAsc(TenantContext.getTenantId()).stream()
                .filter(u -> employeeId.equals(u.getEmployeeId()))
                .findFirst()
                .ifPresent(u -> notifyUser(u.getId(), title, message, link));
    }

    @Transactional
    public void notifyManagerOf(Long employeeId, String title, String message) {
        notifyManagerOf(employeeId, title, message, null);
    }

    @Transactional
    public void notifyManagerOf(Long employeeId, String title, String message, String link) {
        if (employeeId == null) {
            return;
        }
        Employee e = employeeRepository.findById(employeeId).orElse(null);
        if (e == null || e.getManagerId() == null) {
            notifyAdmins(title, message, link);
            return;
        }
        notifyEmployee(e.getManagerId(), title, message, link);
    }

    @Transactional
    public void notifyAdmins(String title, String message) {
        notifyAdmins(title, message, null);
    }

    @Transactional
    public void notifyAdmins(String title, String message, String link) {
        Long tenantId = TenantContext.getTenantId();
        for (User u : userRepository.findByTenantIdOrderByLastNameAscFirstNameAsc(tenantId)) {
            if (u.getRole() == UserRole.ADMIN) {
                notifyUser(u.getId(), title, message, link);
            }
        }
    }

    @Transactional
    public void notifyCompany(String title, String message, Long departmentId) {
        notifyCompany(title, message, departmentId, "/portal/announcements", "/app/announcements");
    }

    @Transactional
    public void notifyCompany(String title, String message, Long departmentId, String employeeLink, String staffLink) {
        Long tenantId = TenantContext.getTenantId();
        for (User u : userRepository.findByTenantIdOrderByLastNameAscFirstNameAsc(tenantId)) {
            String link = u.getRole() == UserRole.EMPLOYEE ? employeeLink : staffLink;
            if (departmentId == null) {
                notifyUser(u.getId(), title, message, link);
                continue;
            }
            if (u.getEmployeeId() == null) {
                if (u.getRole() == UserRole.ADMIN) {
                    notifyUser(u.getId(), title, message, staffLink);
                }
                continue;
            }
            Employee e = employeeRepository.findById(u.getEmployeeId()).orElse(null);
            if (e != null && departmentId.equals(e.getDepartmentId())) {
                notifyUser(u.getId(), title, message, link);
            }
        }
    }

    @Transactional
    public Notification markRead(Long id, UserPrincipal actor) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Notification introuvable"));
        if (!actor.getId().equals(n.getUserId())) {
            throw BusinessException.forbidden("Notification d’un autre utilisateur");
        }
        n.setReadFlag(true);
        return notificationRepository.save(n);
    }

    @Transactional
    public int markAllRead(UserPrincipal actor) {
        List<Notification> list = notificationRepository.findByUserIdOrderByCreatedAtDesc(actor.getId());
        int n = 0;
        for (Notification item : list) {
            if (!item.isReadFlag()) {
                item.setReadFlag(true);
                notificationRepository.save(item);
                n++;
            }
        }
        return n;
    }
}
