package ma.rhflow.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import ma.rhflow.domain.entity.Department;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.JobOffer;
import ma.rhflow.domain.entity.LeaveType;
import ma.rhflow.domain.entity.Position;

public final class JsonMaps {

    private JsonMaps() {
    }

    public static String str(Map<String, ?> m, String... keys) {
        if (m == null) {
            return null;
        }
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v).trim();
            }
        }
        return null;
    }

    public static Long lng(Map<String, ?> m, String... keys) {
        String s = str(m, keys);
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s.replace(".0", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer integer(Map<String, ?> m, String... keys) {
        String s = str(m, keys);
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s.replace(".0", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal dec(Map<String, ?> m, String... keys) {
        String s = str(m, keys);
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Boolean bool(Map<String, ?> m, String... keys) {
        String s = str(m, keys);
        if (s == null) {
            return null;
        }
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "oui".equalsIgnoreCase(s);
    }

    public static LocalDate date(Map<String, ?> m, String... keys) {
        String s = str(m, keys);
        if (s == null || s.length() < 10) {
            return null;
        }
        return LocalDate.parse(s.substring(0, 10));
    }

    public static LocalTime time(Map<String, ?> m, String... keys) {
        String s = str(m, keys);
        if (s == null) {
            return null;
        }
        if (s.length() == 5) {
            return LocalTime.parse(s + ":00");
        }
        return LocalTime.parse(s);
    }

    public static Map<String, Object> employeeRef(Employee e) {
        if (e == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("firstName", e.getFirstName());
        m.put("lastName", e.getLastName());
        m.put("email", e.getEmail());
        return m;
    }

    public static Map<String, Object> departmentRef(Department d) {
        if (d == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        return m;
    }

    public static Map<String, Object> positionRef(Position p) {
        if (p == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getTitle());
        m.put("title", p.getTitle());
        return m;
    }

    public static Map<String, Object> leaveTypeRef(LeaveType t) {
        if (t == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("code", t.getCode());
        return m;
    }

    public static Map<String, Object> jobRef(JobOffer o) {
        if (o == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("name", o.getTitle());
        m.put("title", o.getTitle());
        return m;
    }
}
